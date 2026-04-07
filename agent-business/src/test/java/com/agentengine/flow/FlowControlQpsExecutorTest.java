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

class FlowControlQpsExecutorTest {

    private static final Path QPS_REPORT_FILE = Path.of("target", "flow-control-qps-test-report.log");
    private final List<ExecutorService> toShutdown = new ArrayList<>();

    @BeforeAll
    static void initReportFile() throws IOException {
        Files.createDirectories(QPS_REPORT_FILE.getParent());
        Files.writeString(QPS_REPORT_FILE, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @AfterEach
    void cleanup() {
        for (ExecutorService es : toShutdown) {
            es.shutdownNow();
        }
        toShutdown.clear();
    }

    @Test
    void qps100_singleThread_every10ms_shouldMostlyPass() throws Exception {
        ExecutorService executor = newFlowExecutor("test-qps100-single", 4, 100);

        int total = 100;
        int pass = 0;
        int blocked = 0;
        Instant begin = Instant.now();
        for (int i = 0; i < total; i++) {
            try {
                Future<Integer> f = executor.submit(() -> 1);
                f.get(1, TimeUnit.SECONDS);
                pass++;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof ExecutorSaturatedException) {
                    blocked++;
                } else {
                    throw e;
                }
            } catch (ExecutorSaturatedException e) {
                blocked++;
            }
            Thread.sleep(10);
        }
        long costMs = Duration.between(begin, Instant.now()).toMillis();
        printQpsReport("single-thread-10ms", total, pass, blocked, costMs);

        Assertions.assertTrue(pass >= 95, "single-thread 10ms should mostly pass");
    }

    @Test
    void qps100_200Concurrent_once_shouldAboutHalfPass() throws Exception {
        int concurrent = 200;
        ExecutorService executor = newFlowExecutor("test-qps100-200concurrent", 256, 100);

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
                    Future<Integer> f = executor.submit(() -> 1);
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

        printQpsReport("200-concurrent-burst", concurrent, pass.get(), blocked.get(), costMs);
        Assertions.assertTrue(pass.get() >= 80 && pass.get() <= 120,
                "200 concurrent once with qps=100 should be about half pass");
        Assertions.assertEquals(0, failed.get(), "no unexpected failure");
    }

    @Test
    void qps100_crossWindow_after1100ms_shouldPassAgain() throws Exception {
        ExecutorService executor = newFlowExecutor("test-qps100-cross-window", 128, 100);

        BurstResult filled = runBurst(executor, 100, () -> 1);

        boolean blockedNow;
        try {
            executor.submit(() -> 1).get(1, TimeUnit.SECONDS);
            blockedNow = false;
        } catch (ExecutionException e) {
            blockedNow = e.getCause() instanceof ExecutorSaturatedException;
        } catch (ExecutorSaturatedException e) {
            blockedNow = true;
        }

        Thread.sleep(1100);

        boolean passedAfterWindow;
        try {
            executor.submit(() -> 1).get(1, TimeUnit.SECONDS);
            passedAfterWindow = true;
        } catch (ExecutionException e) {
            passedAfterWindow = false;
        }

        printQpsReport(
                "cross-window-1100ms",
                102,
                filled.pass() + (passedAfterWindow ? 1 : 0),
                filled.blocked() + (blockedNow ? 1 : 0),
                1100
        );
        Assertions.assertTrue(blockedNow, "the request right after full window should be blocked");
        Assertions.assertTrue(passedAfterWindow, "request after 1100ms should pass");
    }

    private ExecutorService newFlowExecutor(String namePrefix, int threads, int qps) {
        ExecutorService es = ExecutorFactory.create(
                ExecutorFactory.PoolType.LLM_IO,
                new ExecutorFactory.PoolSpec(
                        "test",
                        namePrefix,
                        threads,
                        threads,
                        true,
                        new ThreadPoolExecutor.AbortPolicy(),
                        true,
                        qps,
                        false,
                        0.5d
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

    private record BurstResult(int pass, int blocked, int failed, long costMs) {
    }

    private void printQpsReport(String scenario, int total, int pass, int blocked, long costMs) {
        String line = String.format(
                "[FlowControlTest] category=QPS scenario=%s total=%d pass=%d blocked=%d costMs=%d%n",
                scenario, total, pass, blocked, costMs
        );
        System.out.print(line);
        try {
            Files.writeString(QPS_REPORT_FILE, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write report file: " + QPS_REPORT_FILE, e);
        }
    }
}

