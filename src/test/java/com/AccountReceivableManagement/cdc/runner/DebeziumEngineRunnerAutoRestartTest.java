package com.AccountReceivableManagement.cdc.runner;

import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Regression test for the "CDC silently stops capturing data until the app is restarted" bug.
 * Previously the runner called debeziumEngine.run() exactly once on a single-thread executor;
 * if run() ever returned or threw (a transient DB hiccup, a handler exception, a lost source
 * connection) nothing rebuilt or resumed the engine, so capture went dark forever until the
 * whole JVM restarted. The runner must now detect the stop and build+run a fresh engine.
 */
class DebeziumEngineRunnerAutoRestartTest {

    @Test
    void engineThatDiesIsAutomaticallyRebuiltAndRestarted() throws Exception {
        AtomicInteger enginesBuilt = new AtomicInteger(0);
        CountDownLatch secondEngineRan = new CountDownLatch(1);

        ProjectCdcConfig.DebeziumEngineRunner runner = new ProjectCdcConfig.DebeziumEngineRunner(
                "Test",
                () -> {
                    int attempt = enginesBuilt.incrementAndGet();
                    @SuppressWarnings("unchecked")
                    DebeziumEngine<ChangeEvent<String, String>> engine = mock(DebeziumEngine.class);
                    try {
                        if (attempt == 1) {
                            // Simulates the engine's run() dying on the first attempt, e.g. due to
                            // a handler exception or a transient source-DB error.
                            org.mockito.Mockito.doThrow(new RuntimeException("simulated engine failure"))
                                    .when(engine).run();
                        } else {
                            org.mockito.Mockito.doAnswer(invocation -> {
                                secondEngineRan.countDown();
                                return null;
                            }).when(engine).run();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    return engine;
                },
                50L // short restart delay so the test doesn't wait on the real 10s production backoff
        );

        runner.start();
        try {
            assertTrue(secondEngineRan.await(5, TimeUnit.SECONDS),
                    "runner should rebuild and run a fresh engine after the first one died, without any manual restart");
            assertTrue(enginesBuilt.get() >= 2, "expected at least 2 engine builds (initial + restart)");
        } finally {
            runner.stop();
        }
    }
}
