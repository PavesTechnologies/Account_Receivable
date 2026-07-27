package com.AccountReceivableManagement.cdc.runner;

import com.AccountReceivableManagement.cdc.listener.ProjectCdcHandler;
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
        value = "project-cdc.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class ProjectCdcConfig {

    @Bean
    public DebeziumEngine<ChangeEvent<String, String>> projectDebeziumEngine(
            @Qualifier("projectDebeziumConfiguration")
            io.debezium.config.Configuration configuration,
            ProjectCdcHandler projectCdcHandler
    ) {

        Properties props = configuration.asProperties();

        log.info("========== Project Debezium Configuration ==========");
        props.forEach((key, value) ->
                log.info("{} = {}", key, value));
        log.info("====================================================");

        return DebeziumEngine.create(Json.class)
                .using(props)
                .notifying(projectCdcHandler::handleEvent)
                .using((success, message, error) -> {
                    if (error != null) {
                        log.error("Project CDC Engine failed: {}", message, error);
                    } else {
                        log.info("Project CDC Engine processing stopped.");
                    }
                })
                .build();
    }

    @Bean
    public DebeziumEngineRunner projectDebeziumEngineRunner(
            @Qualifier("projectDebeziumEngine")
            DebeziumEngine<ChangeEvent<String, String>> projectDebeziumEngine
    ) {
        return new DebeziumEngineRunner(projectDebeziumEngine);
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
            log.info("Starting Project Debezium Engine...");
            executorService.execute(() -> debeziumEngine.run());
            this.running = true;
            log.info("Project Debezium Engine started successfully.");
        }

        @Override
        public void stop() {
            log.info("Stopping Project Debezium Engine...");
            this.running = false;
            try {
                debeziumEngine.close();
            } catch (java.io.IOException e) {
                log.error("Error closing Project Debezium Engine", e);
            }
            executorService.shutdown();
            log.info("Project Debezium Engine stopped.");
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
