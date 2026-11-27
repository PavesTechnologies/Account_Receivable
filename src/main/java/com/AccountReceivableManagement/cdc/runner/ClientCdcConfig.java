package com.AccountReceivableManagement.cdc.runner;

import com.AccountReceivableManagement.cdc.config.CdcConfigurationLogging;
import com.AccountReceivableManagement.cdc.listener.ClientCdcHandler;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

@Slf4j
@Configuration
@ConditionalOnProperty(
        value = "client-cdc.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class ClientCdcConfig {

    // Not a @Bean: the runner below calls this directly (self-invocation) to build a
    // fresh engine instance every time it needs to restart after an unexpected stop.
    // A @Bean method would self-invoke through the CGLIB proxy and keep returning the
    // same cached singleton, defeating the restart.
    public DebeziumEngine<ChangeEvent<String, String>> debeziumEngine(
            io.debezium.config.Configuration configuration,
            ClientCdcHandler clientCdcHandler
    ) {

        Properties props = configuration.asProperties();

        // Never log the whole property map: it holds the source and offset-store passwords. Allowlisted metadata only.
        CdcConfigurationLogging.logSafeSummary(log, "Debezium Configuration", props);

        return DebeziumEngine.create(Json.class)
                .using(props)
                .notifying(clientCdcHandler::handleEvent)
                .using((success, message, error) -> {
                    if (error != null) {
                        log.error("Client CDC Engine failed: {}", message, error);
                    } else {
                        log.info("Client CDC Engine processing stopped.");
                    }
                })
                .build();
    }

    @Bean
    public DebeziumEngineRunner debeziumEngineRunner(
            @Qualifier("clientDebeziumConfiguration")
            io.debezium.config.Configuration configuration,
            ClientCdcHandler clientCdcHandler
    ) {
        Supplier<DebeziumEngine<ChangeEvent<String, String>>> engineSupplier =
                () -> debeziumEngine(configuration, clientCdcHandler);

        return new DebeziumEngineRunner("Client", engineSupplier);
    }

    public static class DebeziumEngineRunner implements SmartLifecycle {

        private static final long DEFAULT_RESTART_DELAY_MS = 10_000;

        private final String engineName;
        private final Supplier<DebeziumEngine<ChangeEvent<String, String>>> engineSupplier;
        private final long restartDelayMs;
        private final ExecutorService executorService;
        private volatile DebeziumEngine<ChangeEvent<String, String>> currentEngine;
        private volatile boolean running = false;

        public DebeziumEngineRunner(String engineName, Supplier<DebeziumEngine<ChangeEvent<String, String>>> engineSupplier) {
            this(engineName, engineSupplier, DEFAULT_RESTART_DELAY_MS);
        }

        // Package-private: lets tests use a short restart delay instead of waiting on the real one.
        DebeziumEngineRunner(String engineName, Supplier<DebeziumEngine<ChangeEvent<String, String>>> engineSupplier, long restartDelayMs) {
            this.engineName = engineName;
            this.engineSupplier = engineSupplier;
            this.restartDelayMs = restartDelayMs;
            this.executorService = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, engineName + "-debezium-engine");
                thread.setDaemon(true);
                return thread;
            });
        }

        @Override
        public void start() {
            log.info("Starting {} Debezium Engine...", engineName);
            running = true;
            executorService.execute(this::runWithAutoRestart);
            log.info("{} Debezium Engine started successfully.", engineName);
        }

        /**
         * The embedded engine's run() call is expected to block for the lifetime of the
         * connector. If it ever returns or throws — a transient DB hiccup, a handler
         * exception, a lost PMS connection — nothing upstream restarts it, so CDC capture
         * silently goes dark until the whole application is restarted. This loop is the fix:
         * it rebuilds a fresh engine and keeps retrying (with backoff) until stop() runs.
         */
        private void runWithAutoRestart() {
            while (running) {
                DebeziumEngine<ChangeEvent<String, String>> engine = engineSupplier.get();
                currentEngine = engine;
                try {
                    engine.run();
                } catch (Exception e) {
                    log.error("{} Debezium Engine terminated with an exception.", engineName, e);
                } finally {
                    closeQuietly(engine);
                }

                if (!running) {
                    break;
                }

                log.warn("{} Debezium Engine stopped unexpectedly. Restarting in {} ms...", engineName, restartDelayMs);
                try {
                    Thread.sleep(restartDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        private void closeQuietly(DebeziumEngine<ChangeEvent<String, String>> engine) {
            if (engine == null) {
                return;
            }
            try {
                engine.close();
            } catch (java.io.IOException e) {
                log.error("Error closing {} Debezium Engine", engineName, e);
            }
        }

        @Override
        public void stop() {
            log.info("Stopping {} Debezium Engine...", engineName);
            running = false;
            closeQuietly(currentEngine);
            executorService.shutdownNow();
            log.info("{} Debezium Engine stopped.", engineName);
        }

        @Override
        public boolean isRunning() {
            return this.running;
        }

        @Override
        public boolean isAutoStartup() {
            return true;
        }

        @Override
        public void stop(Runnable callback) {
            stop();
            callback.run();
        }

        @Override
        public int getPhase() {
            // Start after table initializer (which has Integer.MIN_VALUE)
            return 0;
        }
    }
}
