package com.AccountReceivableManagement.cdc.listener;

import com.AccountReceivableManagement.cdc.payload.CdcEventPayload;
import com.AccountReceivableManagement.cdc.service.CdcInboxService;
import com.AccountReceivableManagement.util.CdcEventParser;
import io.debezium.engine.ChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectCdcHandler {

    private final CdcInboxService cdcInboxService;
    private final CdcEventParser cdcEventParser;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleEvent(ChangeEvent<String,String> event) {

        log.info("******** PROJECT HANDLE EVENT CALLED ********");
        try {
            CdcEventPayload payload = cdcEventParser.parse(event);

            if (payload == null) {
                log.warn("Parsed CDC event is null. Skipping. Event: {}", event.value());
                return;
            }

            log.info("Received CDC event: Operation={}, EntityType={}, EntityId={}",
                    payload.getOperation(), payload.getEntityType(), payload.getEntityId());

            cdcInboxService.saveEvent(payload);

        } catch (Exception e) {
            log.error("Failed to handle CDC event. Event: {}", event.value(), e);
            // Optionally, re-throw to let the Debezium engine's error handler manage it
            throw new RuntimeException("Failed to process CDC event", e);
        }
    }
}
