package com.agentengine.flow;

import com.agentcommon.concurrent.ExecutorFactory;
import com.agentcommon.concurrent.ExecutorSaturatedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class FlowControlBreakerExecutorTest {

    private static final Path BREAKER_REPORT_FILE = resolveBreakerReportFile();
    private final List<ExecutorService> toShutdown = new ArrayList<>();

    @BeforeAll
    static void initReportFile() throws IOException {
        Files.createDirectories(BREAKER_REPORT_FILE.getParent());
        Files.writeString(BREAKER_REPORT_FILE, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static Path resolveBreakerReportFile() {
        String multiModuleRoot = System.getProperty("maven.multiModuleProjectDirectory");
        if (multiModuleRoot != null && !multiModuleRoot.isBlank()) {
            return Path.of(multiModuleRoot, "agent-business", "target", "flow-control-breaker-test-report.log");
        }
        return Path.of("target", "flow-control-breaker-test-report.log").toAbsolutePath();
    }

    @AfterEach
    void cleanup() {
        for (ExecutorService es : toShutdown) {
            es.shutdownNow();
        }
        toShutdown.clear();
    }

    @Test
    void breakerOnly_singleFailure_shouldNotTripImmediatelyWhenSamplesLessThanFive() throws Exception {
        ExecutorService executor = newBreakerOnlyExecutor("test-breaker-min-samples", 16, 0.5d);

        try {
            executor.submit(() -> {
                throw new RuntimeException("expected");
            }).get(1, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            // expected business failure
        }

        boolean nextAllowed;
        try {
            executor.submit(() -> 1).get(1, TimeUnit.SECONDS);
            nextAllowed = true;
        } catch (ExecutionException e) {
            nextAllowed = !(e.getCause() instanceof ExecutorSaturatedException);
        } catch (ExecutorSaturatedException e) {
            nextAllowed = false;
        }
        Assertions.assertTrue(nextAllowed, "samples < 5 should always allow request");
    }

    @Test
    void breakerOnly_threshold50_allSuccess_shouldPass() throws Exception {
        int concurrent = 100;
        ExecutorService executor = newBreakerOnlyExecutor("test-breaker-success", 128, 0.5d);
        BurstResult result = runBurst(executor, concurrent, () -> 1);
        printBreakerReport("breaker-only-all-success", concurrent, result.allowed(), result.success(), result.blocked(), result.failed(), result.costMs());
        Assertions.assertEquals(concurrent, result.allowed());
        Assertions.assertEquals(concurrent, result.success());
        Assertions.assertEquals(0, result.blocked());
        Assertions.assertEquals(0, result.failed());
    }

    @Test
    void breakerOnly_threshold50_allFail_thenNextShouldBeBlocked() throws Exception {
        int concurrent = 100;
        ExecutorService executor = newBreakerOnlyExecutor("test-breaker-fail", 128, 0.5d);
        BurstResult failedBurst = runBurst(executor, concurrent, () -> {
            throw new RuntimeException("expected");
        });
        printBreakerReport("breaker-only-all-fail", concurrent, failedBurst.allowed(), failedBurst.success(), failedBurst.blocked(), failedBurst.failed(), failedBurst.costMs());

        boolean nextBlocked;
        try {
            executor.submit(() -> 1).get(1, TimeUnit.SECONDS);
            nextBlocked = false;
        } catch (ExecutionException e) {
            nextBlocked = e.getCause() instanceof ExecutorSaturatedException;
        } catch (ExecutorSaturatedException e) {
            nextBlocked = true;
        }
        Assertions.assertTrue(nextBlocked, "after failure burst, next request should be blocked by breaker");
    }

    @Test
    void breakerOnly_threshold50_crossWindow_after11s_shouldRecover() throws Exception {
        int concurrent = 100;
        ExecutorService executor = newBreakerOnlyExecutor("test-breaker-recover", 128, 0.5d);
        BurstResult failedBurst = runBurst(executor, concurrent, () -> {
            throw new RuntimeException("expected");
        });

        boolean blockedNow;
        try {
            executor.submit(() -> 1).get(1, TimeUnit.SECONDS);
            blockedNow = false;
        } catch (ExecutionException e) {
            blockedNow = e.getCause() instanceof ExecutorSaturatedException;
        } catch (ExecutorSaturatedException e) {
            blockedNow = true;
        }
        Assertions.assertTrue(blockedNow, "breaker should block immediately after failure burst");

        Thread.sleep(11_000L);

        boolean recovered;
        try {
            executor.submit(() -> 1).get(1, TimeUnit.SECONDS);
            recovered = true;
        } catch (ExecutionException | ExecutorSaturatedException e) {
            recovered = false;
        }
        int probeSuccess = recovered ? 1 : 0;
        int probeBlocked = blockedNow ? 1 : 0;
        int probeBizFailed = recovered ? 0 : 1;

        int total = concurrent + 2;
        int allowed = failedBurst.allowed() + (probeSuccess + probeBizFailed);
        int success = failedBurst.success() + probeSuccess;
        int blocked = failedBurst.blocked() + probeBlocked;
        int bizFailed = failedBurst.failed() + probeBizFailed;
        printBreakerReport("breaker-only-cross-window-11s", total, allowed, success, blocked, bizFailed, 11_000);
        Assertions.assertTrue(recovered, "after 11s window slide, request should recover");
    }

    @Test
    void breakerOnly_halfOpen_success_allowsOnlyOneProbe() throws Exception {
        ExecutorService executor = newBreakerOnlyExecutor("test-breaker-half-open-success", 32, 0.5d);
        runBurst(executor, 100, () -> {
            throw new RuntimeException("expected");
        });

        // OPEN 窗口是 5s，等待进入 HALF_OPEN 探测时机
        Thread.sleep(6_000L);

        PairResult pair = runConcurrentPair(executor, () -> {
            Thread.sleep(200L);
            return 1;
        });
        printBreakerReport(
                "breaker-only-half-open-success-pair",
                2,
                pair.allowed(),
                pair.success(),
                pair.blocked(),
                pair.bizFailed(),
                0
        );

        Assertions.assertEquals(1, pair.allowed(), "half-open should allow only one probe request");
        Assertions.assertEquals(1, pair.success(), "the allowed probe should succeed");
        Assertions.assertEquals(1, pair.blocked(), "the other concurrent request should be blocked");
        Assertions.assertEquals(0, pair.bizFailed(), "no biz failure in success probe case");
    }

    @Test
    void breakerOnly_halfOpen_failure_allowsOnlyOneProbe_thenReopen() throws Exception {
        ExecutorService executor = newBreakerOnlyExecutor("test-breaker-half-open-fail", 32, 0.5d);
        runBurst(executor, 100, () -> {
            throw new RuntimeException("expected");
        });

        // OPEN 窗口是 5s，等待进入 HALF_OPEN 探测时机
        Thread.sleep(6_000L);

        PairResult pair = runConcurrentPair(executor, () -> {
            throw new RuntimeException("probe failed");
        });
        printBreakerReport(
                "breaker-only-half-open-fail-pair",
                2,
                pair.allowed(),
                pair.success(),
                pair.blocked(),
                pair.bizFailed(),
                0
        );

        Assertions.assertEquals(1, pair.allowed(), "half-open should allow only one probe request");
        Assertions.assertEquals(0, pair.success(), "the allowed probe should fail");
        Assertions.assertEquals(1, pair.blocked(), "the other concurrent request should be blocked");
        Assertions.assertEquals(1, pair.bizFailed(), "one allowed probe should fail in business");

        boolean blockedAfterFail;
        try {
            executor.submit(() -> 1).get(1, TimeUnit.SECONDS);
            blockedAfterFail = false;
        } catch (ExecutionException e) {
            blockedAfterFail = e.getCause() instanceof ExecutorSaturatedException;
        } catch (ExecutorSaturatedException e) {
            blockedAfterFail = true;
        }
        Assertions.assertTrue(blockedAfterFail, "half-open failure should transition back to OPEN");
    }

    @Test
    void breakerOnly_stateMemoryChain_openHalfOpenThenCloseOrOpen() throws Exception {
        ExecutorService successPath = newBreakerOnlyExecutor("test-breaker-memory-success", 32, 0.5d);
        runBurst(successPath, 100, () -> {
            throw new RuntimeException("expected");
        });

        // 1) OPEN 记忆：未到重试时间前，继续拒绝
        boolean blockedInOpen = isBlocked(successPath);
        Assertions.assertTrue(blockedInOpen, "request should be blocked while state is OPEN before retry time");

        // 2) HALF_OPEN 记忆：到重试点后并发仅放一个探测
        Thread.sleep(6_000L);
        PairResult halfOpenSuccessPair = runConcurrentPair(successPath, () -> {
            Thread.sleep(200L);
            return 1;
        });
        Assertions.assertEquals(1, halfOpenSuccessPair.allowed(), "HALF_OPEN should allow only one probe");
        Assertions.assertEquals(1, halfOpenSuccessPair.success(), "the single probe should succeed");
        Assertions.assertEquals(1, halfOpenSuccessPair.blocked(), "other concurrent request should be blocked");

        // 3) HALF_OPEN -> CLOSED 后记忆：后续应持续放行
        BurstResult afterCloseBurst = runBurst(successPath, 20, () -> 1);
        Assertions.assertEquals(20, afterCloseBurst.allowed(), "after HALF_OPEN success, state should be CLOSED");
        Assertions.assertEquals(20, afterCloseBurst.success(), "after CLOSED, follow-up requests should pass");

        ExecutorService failPath = newBreakerOnlyExecutor("test-breaker-memory-fail", 32, 0.5d);
        runBurst(failPath, 100, () -> {
            throw new RuntimeException("expected");
        });
        Thread.sleep(6_000L);

        PairResult halfOpenFailPair = runConcurrentPair(failPath, () -> {
            throw new RuntimeException("probe failed");
        });
        Assertions.assertEquals(1, halfOpenFailPair.allowed(), "HALF_OPEN should still allow only one probe");
        Assertions.assertEquals(1, halfOpenFailPair.bizFailed(), "the single allowed probe should fail");
        Assertions.assertEquals(1, halfOpenFailPair.blocked(), "other concurrent request should be blocked");

        // HALF_OPEN -> OPEN 后记忆：后续仍拒绝，直到下一次重试时间
        boolean blockedAfterReopen = isBlocked(failPath);
        Assertions.assertTrue(blockedAfterReopen, "after HALF_OPEN failure, breaker should return to OPEN");
    }

    private ExecutorService newBreakerOnlyExecutor(String namePrefix, int threads, double failureRateThreshold) {
        ExecutorService es = ExecutorFactory.create(
                ExecutorFactory.PoolType.LLM_IO,
                new ExecutorFactory.PoolSpec(
                        "test",
                        namePrefix,
                        threads,
                        threads,
                        true,
                        new ThreadPoolExecutor.AbortPolicy(),
                        false,
                        0,
                        true,
                        failureRateThreshold
                )
        );
        toShutdown.add(es);
        return es;
    }

    private BurstResult runBurst(ExecutorService executor, int concurrent, Callable<Integer> task) throws Exception {
        CountDownLatch ready = new CountDownLatch(concurrent);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrent);
        AtomicInteger pass = new AtomicInteger();
        AtomicInteger blocked = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        ExecutorService launcher = Executors.newFixedThreadPool(concurrent);
        toShutdown.add(launcher);

        Instant begin = Instant.now();
        for (int i = 0; i < concurrent; i++) {
            launcher.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    Future<Integer> f = executor.submit(task);
                    f.get(2, TimeUnit.SECONDS);
                    pass.incrementAndGet();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof ExecutorSaturatedException) {
                        blocked.incrementAndGet();
                    } else {
                        failed.incrementAndGet();
                    }
                } catch (ExecutorSaturatedException e) {
                    blocked.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        long costMs = Duration.between(begin, Instant.now()).toMillis();
        return new BurstResult(pass.get(), blocked.get(), failed.get(), costMs);
    }

    private PairResult runConcurrentPair(ExecutorService executor, Callable<Integer> task) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger blocked = new AtomicInteger();
        AtomicInteger bizFailed = new AtomicInteger();

        ExecutorService launcher = Executors.newFixedThreadPool(2);
        toShutdown.add(launcher);

        for (int i = 0; i < 2; i++) {
            launcher.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    executor.submit(task).get(2, TimeUnit.SECONDS);
                    success.incrementAndGet();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof ExecutorSaturatedException) {
                        blocked.incrementAndGet();
                    } else {
                        bizFailed.incrementAndGet();
                    }
                } catch (ExecutorSaturatedException e) {
                    blocked.incrementAndGet();
                } catch (Exception e) {
                    bizFailed.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(2, TimeUnit.SECONDS);
        start.countDown();
        done.await(5, TimeUnit.SECONDS);
        return new PairResult(success.get(), blocked.get(), bizFailed.get());
    }

    private boolean isBlocked(ExecutorService executor) throws Exception {
        try {
            executor.submit(() -> 1).get(1, TimeUnit.SECONDS);
            return false;
        } catch (ExecutionException e) {
            return e.getCause() instanceof ExecutorSaturatedException;
        } catch (ExecutorSaturatedException e) {
            return true;
        }
    }

    private record BurstResult(int success, int blocked, int failed, long costMs) {
        int allowed() {
            return success + failed;
        }
    }

    private record PairResult(int success, int blocked, int bizFailed) {
        int allowed() {
            return success + bizFailed;
        }
    }

    private void printBreakerReport(String scenario, int total, int allowed, int success, int blocked, int bizFailed, long costMs) {
        String line = String.format(
                "[FlowControlTest] category=BREAKER scenario=%s total=%d allowed=%d success=%d blocked=%d bizFailed=%d costMs=%d%n",
                scenario, total, allowed, success, blocked, bizFailed, costMs
        );
        System.out.print(line);
        try {
            Files.writeString(BREAKER_REPORT_FILE, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write report file: " + BREAKER_REPORT_FILE, e);
        }
    }
}
