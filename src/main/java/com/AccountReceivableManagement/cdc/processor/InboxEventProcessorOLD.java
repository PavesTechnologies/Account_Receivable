package com.AccountReceivableManagement.cdc.processor;

import com.AccountReceivableManagement.cdc.entity.CdcInbox;
import com.AccountReceivableManagement.cdc.service.CdcInboxService;
import com.AccountReceivableManagement.cdc.service.ClientDataProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class InboxEventProcessorOLD {

    private final CdcInboxService cdcInboxService;
    private final ClientDataProcessor clientDataProcessor;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000) // Run every 5 seconds
    @Transactional
    public void processPendingEvents() {
        List<CdcInbox> pendingEvents = cdcInboxService.findPendingEvents();
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Found {} pending CDC events to process.", pendingEvents.size());

        for (CdcInbox event : pendingEvents) {
            try {
                processEvent(event);
                cdcInboxService.markAsProcessed(event.getId());
            } catch (Exception e) {
                log.error("Failed to process inbox event ID: {}. Error: {}", event.getId(), e.getMessage(), e);
                cdcInboxService.markAsFailed(event.getId(), e.getMessage());
            }
        }
    }

    private void processEvent(CdcInbox event) throws Exception {
        if ("RMS-client".equals(event.getEntityType())) {
            clientDataProcessor.process(cdcInboxService.deserializePayload(event.getPayload()));
        } else {
            log.warn("Unknown entity type: {}", event.getEntityType());
            throw new IllegalArgumentException("Unknown entity type: " + event.getEntityType());
        }
    }
}
