package com.agentcommon.ratelimit;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * 10s sliding-window circuit breaker with state machine:
 * CLOSED -> OPEN -> HALF_OPEN.
 */
public class SlidingWindowCircuitBreaker {

    public enum State {
        CLOSED(0),
        OPEN(1),
        HALF_OPEN(2);

        private final int code;

        State(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static State fromCode(int code) {
            if (code == 1) {
                return OPEN;
            }
            if (code == 2) {
                return HALF_OPEN;
            }
            return CLOSED;
        }
    }

    public record AcquireDecision(boolean allowed, State admittedState) {
        public static AcquireDecision denied() {
            return new AcquireDecision(false, null);
        }

        public static AcquireDecision allowed(State state) {
            return new AcquireDecision(true, state);
        }
    }

    private static final int TOTAL_BUCKETS = 10;
    private static final int WINDOW_BUCKETS = 10;
    private static final int MAX_COUNTER = 0xFFFF;
    private static final int MIN_SAMPLES_TO_TRIP = 5;
    private static final int OPEN_SECONDS = 5;

    private final double failureRateThreshold;
    private final AtomicLongArray buckets = new AtomicLongArray(TOTAL_BUCKETS);
    /**
     * High 32 bits: state code, low 32 bits: nextRetryEpochSecond.
     */
    private final AtomicLong stateWord = new AtomicLong(packState(State.CLOSED.code(), 0));

    public SlidingWindowCircuitBreaker(double failureRateThreshold) {
        this.failureRateThreshold = failureRateThreshold;
    }

    public AcquireDecision tryAcquire() {
        while (true) {
            long word = stateWord.get();
            State state = State.fromCode(unpackStateCode(word));
            int nextRetryEpochSec = unpackNextRetryEpochSec(word);
            int now = currentTick();

            if (state == State.OPEN) {
                if (now < nextRetryEpochSec) {
                    return AcquireDecision.denied();
                }
                long nextWord = packState(State.HALF_OPEN.code(), 0);
                if (stateWord.compareAndSet(word, nextWord)) {
                    return AcquireDecision.allowed(State.HALF_OPEN);
                }
                continue;
            }

            if (state == State.HALF_OPEN) {
                return AcquireDecision.denied();
            }

            Snapshot s = snapshot(now);
            if (s.total() < MIN_SAMPLES_TO_TRIP) {
                return AcquireDecision.allowed(State.CLOSED);
            }
            if (s.failureRate() < failureRateThreshold) {
                return AcquireDecision.allowed(State.CLOSED);
            }

            int nextRetry = now + OPEN_SECONDS;
            long nextWord = packState(State.OPEN.code(), nextRetry);
            stateWord.compareAndSet(word, nextWord);
            return AcquireDecision.denied();
        }
    }

    public void onComplete(AcquireDecision decision, boolean success) {
        if (decision == null || !decision.allowed() || decision.admittedState() == null) {
            return;
        }
        record(success);
        if (decision.admittedState() == State.HALF_OPEN) {
            transitionAfterHalfOpenResult(success);
        }
    }

    private void transitionAfterHalfOpenResult(boolean success) {
        long expected = packState(State.HALF_OPEN.code(), 0);
        if (success) {
            // HALF_OPEN -> CLOSED: clear counters first to start a fresh period.
            clearBuckets();
            stateWord.compareAndSet(expected, packState(State.CLOSED.code(), 0));
            return;
        }
        int now = currentTick();
        stateWord.compareAndSet(expected, packState(State.OPEN.code(), now + OPEN_SECONDS));
    }

    private void clearBuckets() {
        for (int i = 0; i < TOTAL_BUCKETS; i++) {
            buckets.set(i, 0L);
        }
    }

    private Snapshot snapshot(int nowTick) {
        long success = 0;
        long failure = 0;
        for (int i = 0; i < WINDOW_BUCKETS; i++) {
            int tick = nowTick - i;
            int idx = Math.floorMod(tick, TOTAL_BUCKETS);
            long packed = buckets.get(idx);
            int bucketTick = unpackTick(packed);
            if (bucketTick == tick) {
                success += unpackSuccess(packed);
                failure += unpackFailure(packed);
            }
        }
        long total = success + failure;
        double failureRate = total <= 0 ? 0.0d : failure / (double) total;
        return new Snapshot(success, failure, total, failureRate);
    }

    private record Snapshot(long success, long failure, long total, double failureRate) {
    }

    private static long packState(int stateCode, int nextRetryEpochSec) {
        return ((long) stateCode << 32) | (nextRetryEpochSec & 0xFFFFFFFFL);
    }

    private static int unpackStateCode(long word) {
        return (int) (word >>> 32);
    }

    private static int unpackNextRetryEpochSec(long word) {
        return (int) word;
    }

    public State currentState() {
        return State.fromCode(unpackStateCode(stateWord.get()));
    }

    public int currentNextRetryEpochSec() {
        return unpackNextRetryEpochSec(stateWord.get());
    }

    private void record(boolean success) {
        while (true) {
            int tick = currentTick();
            int idx = Math.floorMod(tick, TOTAL_BUCKETS);
            long oldVal = buckets.get(idx);
            int oldTick = unpackTick(oldVal);

            int nextSuccess;
            int nextFailure;
            if (oldTick != tick) {
                nextSuccess = success ? 1 : 0;
                nextFailure = success ? 0 : 1;
            } else {
                int oldSuccess = unpackSuccess(oldVal);
                int oldFailure = unpackFailure(oldVal);
                nextSuccess = success ? Math.min(MAX_COUNTER, oldSuccess + 1) : oldSuccess;
                nextFailure = success ? oldFailure : Math.min(MAX_COUNTER, oldFailure + 1);
            }

            long nextVal = pack(tick, nextSuccess, nextFailure);
            if (buckets.compareAndSet(idx, oldVal, nextVal)) {
                return;
            }
        }
    }

    private int currentTick() {
        return (int) (System.currentTimeMillis() / 1000L);
    }

    private static long pack(int tick, int success, int failure) {
        long lower32 = ((long) (success & 0xFFFF) << 16) | (failure & 0xFFFFL);
        return ((long) tick << 32) | lower32;
    }

    private static int unpackTick(long packed) {
        return (int) (packed >>> 32);
    }

    private static int unpackSuccess(long packed) {
        return (int) ((packed >>> 16) & 0xFFFF);
    }

    private static int unpackFailure(long packed) {
        return (int) (packed & 0xFFFF);
    }
}

