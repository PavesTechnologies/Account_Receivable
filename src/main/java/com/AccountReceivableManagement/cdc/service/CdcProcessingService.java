package com.AccountReceivableManagement.cdc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CdcProcessingService {

//    private final CdcInboxService cdcInboxService;
//
//    public void processPendingEvents() {
//        List<CdcInbox> pendingEvents = cdcInboxService.findPendingEvents();
//        log.info("Found {} pending CDC events to process", pendingEvents.size());
//
//        for (CdcInbox event : pendingEvents) {
//            try {
//                CdcEventPayload payload = cdcInboxService.deserializePayload(event.getPayload());
//
//                // TODO: Implement business logic based on event type and operation
//                log.info("Processing event: ID={}, EntityType={}, EntityId={}, Operation={}",
//                        event.getId(), payload.getEntityType(), payload.getEntityId(), payload.getOperation());
//
//                // Mark as processed
//                cdcInboxService.markAsProcessed(event.getId());
//
//            } catch (Exception e) {
//                log.error("Failed to process CDC event: ID={}", event.getId(), e);
//                cdcInboxService.markAsFailed(event.getId(), e.getMessage());
//            }
//        }
//    }
}
