package com.agentcommon.ratelimit;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * 基于循环数组的滑动窗口 QPS 限流器。
 * <p>
 * - 桶粒度：100ms
 * - 总桶数：20（用于时间轮复用）
 * - 统计窗口：最近10个桶（约1秒）
 * </p>
 */
public class SlidingWindowQpsLimiter {

    private static final int BUCKET_MILLIS = 100;
    private static final int TOTAL_BUCKETS = 10;
    private static final int WINDOW_BUCKETS = 10;

    private final int qpsLimit;
    private final AtomicLongArray buckets = new AtomicLongArray(TOTAL_BUCKETS);

    public SlidingWindowQpsLimiter(int qpsLimit) {
        this.qpsLimit = qpsLimit;
    }

    /**
     * 尝试获取一次令牌。
     *
     * @return true=放行，false=限流
     */
    public boolean tryAcquire() {
        if (qpsLimit <= 0) {
            return false;
        }
        while (true) {
            int tick = currentTick();
            int snapshot = sumRecentWindow(tick);
            if (snapshot >= qpsLimit) {
                return false;
            }
            if (casIncrementCurrentBucket(tick)) {
                return true;
            }
            // CAS 失败说明存在并发竞争，重读快照后再判断。
        }
    }

    private int sumRecentWindow(int currentTick) {
        int total = 0;
        for (int i = 0; i < WINDOW_BUCKETS; i++) {
            int tick = currentTick - i;
            int idx = Math.floorMod(tick, TOTAL_BUCKETS);
            long packed = buckets.get(idx);
            int bucketTick = unpackTick(packed);
            if (bucketTick == tick) {
                total += unpackCount(packed);
            }
        }
        return total;
    }

    private boolean casIncrementCurrentBucket(int currentTick) {
        int idx = Math.floorMod(currentTick, TOTAL_BUCKETS);
        long oldVal = buckets.get(idx);
        int oldTick = unpackTick(oldVal);
        int oldCount = unpackCount(oldVal);
        long nextVal;
        if (oldTick != currentTick) {
            // 时间戳不一致，等价于清零后再+1。
            nextVal = pack(currentTick, 1);
        } else {
            nextVal = pack(currentTick, oldCount + 1);
        }
        // CAS 失败说明发生并发竞争，交给外层重算窗口总和后再决策。
        return buckets.compareAndSet(idx, oldVal, nextVal);
    }

    private int currentTick() {
        return (int) (System.currentTimeMillis() / BUCKET_MILLIS);
    }

    private static long pack(int tick, int count) {
        return ((long) tick << 32) | (count & 0xFFFF_FFFFL);
    }

    private static int unpackTick(long packed) {
        return (int) (packed >>> 32);
    }

    private static int unpackCount(long packed) {
        return (int) packed;
    }
}
