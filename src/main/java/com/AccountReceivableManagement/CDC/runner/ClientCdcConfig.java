package com.AccountReceivableManagement.CDC.runner;

import com.AccountReceivableManagement.CDC.listener.ClientCdcHandler;
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

@Slf4j
@Configuration
@ConditionalOnProperty(
        value = "client-cdc.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class ClientCdcConfig {

    @Bean
    public DebeziumEngine<ChangeEvent<String, String>> debeziumEngine(
            @Qualifier("clientDebeziumConfiguration")
            io.debezium.config.Configuration configuration,
            ClientCdcHandler clientCdcHandler
    ) {

        Properties props = configuration.asProperties();

        log.info("========== Debezium Configuration ==========");
        props.forEach((key, value) ->
                log.info("{} = {}", key, value));
        log.info("============================================");

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
            @Qualifier("debeziumEngine")
            DebeziumEngine<ChangeEvent<String, String>> debeziumEngine
    ) {
        return new DebeziumEngineRunner(debeziumEngine);
    }

    public static class DebeziumEngineRunner implements SmartLifecycle {
        private final DebeziumEngine<ChangeEvent<String, String>> debeziumEngine;
        private final ExecutorService executorService;
        private volatile boolean running = false;

        public DebeziumEngineRunner(DebeziumEngine<ChangeEvent<String, String>> debeziumEngine) {
            this.debeziumEngine = debeziumEngine;
            this.executorService = Executors.newSingleThreadExecutor();
        }

        @Override
        public void start() {
            log.info("Starting Debezium Engine...");
            executorService.execute(() -> debeziumEngine.run());
            this.running = true;
            log.info("Debezium Engine started successfully.");
        }

        @Override
        public void stop() {
            log.info("Stopping Debezium Engine...");
            this.running = false;
            try {
                debeziumEngine.close();
            } catch (java.io.IOException e) {
                log.error("Error closing Debezium Engine", e);
            }
            executorService.shutdown();
            log.info("Debezium Engine stopped.");
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
