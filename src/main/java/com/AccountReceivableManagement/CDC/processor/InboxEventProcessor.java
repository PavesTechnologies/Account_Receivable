package com.AccountReceivableManagement.CDC.processor;

import com.AccountReceivableManagement.CDC.entity.CdcInbox;
import com.AccountReceivableManagement.CDC.payload.CdcEventPayload;
import com.AccountReceivableManagement.CDC.service.CdcInboxService;
import com.AccountReceivableManagement.service.ClientDataProcessor;
import com.AccountReceivableManagement.service.ProjectDataProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class InboxEventProcessor {

    private final CdcInboxService cdcInboxService;
    private final ClientDataProcessor clientDataProcessor;
    private final ProjectDataProcessor projectDataProcessor;

    /**
     * Process pending CDC events from the inbox.
     * Runs every 5 seconds by default.
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void processPendingEvents() {
        try {
            List<CdcInbox> pendingEvents = cdcInboxService.findPendingEvents();

            if (pendingEvents.isEmpty()) {
                return;
            }

            log.info("Found {} pending CDC events to process", pendingEvents.size());

            for (CdcInbox inboxEvent : pendingEvents) {
                processSingleEvent(inboxEvent);
            }

        } catch (Exception e) {
            log.error("Error during CDC inbox event processing", e);
        }
    }

    /**
     * Process a single inbox event with idempotency and error handling.
     */
    private void processSingleEvent(CdcInbox inboxEvent) {
        try {
            log.info("Processing CDC inbox event - ID: {}, EntityType: {}, EntityId: {}, Operation: {}",
                    inboxEvent.getId(), inboxEvent.getEntityType(), inboxEvent.getEntityId(), inboxEvent.getOperation());

            // Deserialize payload
            CdcEventPayload payload = cdcInboxService.deserializePayload(inboxEvent.getPayload());

            // Route to appropriate handler based on connector/entity type
            routeEvent(payload, inboxEvent);

            // Mark as processed on success
            cdcInboxService.markAsProcessed(inboxEvent.getId());

            log.info("Successfully processed CDC inbox event - ID: {}", inboxEvent.getId());

        } catch (Exception e) {
            log.error("Failed to process CDC inbox event - ID: {}, EntityType: {}, EntityId: {}",
                    inboxEvent.getId(), inboxEvent.getEntityType(), inboxEvent.getEntityId(), e);

            // Mark as failed with error message for retry
            cdcInboxService.markAsFailed(inboxEvent.getId(), e.getMessage());
        }
    }

    /**
     * Route the event to the appropriate handler based on connector/entity type.
     * This method is designed to be generic for future handlers (Invoice, Project, Employee, etc.).
     */
    private void routeEvent(CdcEventPayload payload, CdcInbox inboxEvent) {

        String entityType = inboxEvent.getEntityType();

        log.info("Routing CDC event - Connector: {}, EntityType: {}",
                inboxEvent.getConnectorName(),
                entityType);

        switch (entityType.toUpperCase()) {

            case "RMS-CLIENT":
                log.info("Routing to ClientDataProcessor");
                clientDataProcessor.process(payload);
                break;

            case "PMS-PROJECTS":
                log.info("Routing to ProjectDataProcessor");
                projectDataProcessor.process(payload);
                break;

            // Future handlers
            // case "RMS-EMPLOYEE":
            //     employeeDataProcessor.process(payload);
            //     break;
            //
            // case "RMS-INVOICE":
            //     invoiceDataProcessor.process(payload);
            //     break;

            default:
                String errorMsg = String.format(
                        "No handler found for Connector=%s, EntityType=%s",
                        inboxEvent.getConnectorName(),
                        entityType
                );
                log.error(errorMsg);
                throw new IllegalArgumentException(errorMsg);
        }
    }

    /**
     * Process failed events that are eligible for retry.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void retryFailedEvents() {
        try {
            int maxRetries = 3;
            List<CdcInbox> failedEvents = cdcInboxService.findFailedEventsForRetry(maxRetries);

            if (failedEvents.isEmpty()) {
                return;
            }

            log.info("Found {} failed CDC events eligible for retry", failedEvents.size());

            for (CdcInbox failedEvent : failedEvents) {
                log.info("Retrying failed CDC event - ID: {}, RetryCount: {}",
                        failedEvent.getId(), failedEvent.getRetryCount());

                // Mark as pending for reprocessing
                cdcInboxService.markForRetry(failedEvent.getId());
            }

        } catch (Exception e) {
            log.error("Error during CDC failed event retry processing", e);
        }
    }
}
