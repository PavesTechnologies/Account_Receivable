package com.AccountReceivableManagement.cdc.runner;

import com.AccountReceivableManagement.cdc.config.properties.ClientCdcProperties;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class ClientCdcRunner {

    private final ClientCdcProperties clientCdcProperties;
    private final DebeziumEngine<ChangeEvent<String, String>> debeziumEngine;
    private final ExecutorService executorService;

    public ClientCdcRunner(
            ClientCdcProperties clientCdcProperties,
            DebeziumEngine<ChangeEvent<String, String>> debeziumEngine
    ) {
        this.clientCdcProperties = clientCdcProperties;
        this.debeziumEngine = debeziumEngine;
        this.executorService = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "debezium-client-cdc-engine")
        );
    }

//    @PostConstruct
//    public void startDebeziumEngine() {
//        if (!clientCdcProperties.isEnabled()) {
//            log.warn("Client CDC is disabled by configuration. Skipping engine startup.");
//            return;
//        }
//
//        log.info("Starting Client CDC Debezium Engine...");
//        this.executorService.execute(this.debeziumEngine);
//        log.info("Client CDC Debezium Engine started.");
//    }

//    @PreDestroy
//    public void stopDebeziumEngine() {
//        log.info("Stopping Client CDC Debezium Engine...");
//        try {
//            this.debeziumEngine.close();
//            this.executorService.shutdown();
//            if (!this.executorService.awaitTermination(60, TimeUnit.SECONDS)) {
//                log.warn("Executor service did not terminate gracefully after 60 seconds. Forcing shutdown.");
//                this.executorService.shutdownNow();
//            }
//            log.info("Client CDC Debezium Engine stopped successfully.");
//        } catch (IOException | InterruptedException e) {
//            log.error("Error stopping Client CDC Debezium Engine", e);
//            Thread.currentThread().interrupt();
//        }
//    }
}
