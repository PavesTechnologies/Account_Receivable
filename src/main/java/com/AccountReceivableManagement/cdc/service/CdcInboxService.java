package com.AccountReceivableManagement.cdc.service;

import com.AccountReceivableManagement.cdc.entity.CdcInbox;
import com.AccountReceivableManagement.cdc.payload.CdcEventPayload;
import com.AccountReceivableManagement.cdc.repository.CdcInboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CdcInboxService {

    /**
     * Once an event's retryCount reaches this, {@link #findFailedEventsForRetry} permanently
     * excludes it - there is no further automatic attempt. Shared with InboxEventProcessor's
     * retry query so the "still eligible" and "exhausted" checks never drift apart.
     */
    public static final int MAX_RETRIES = 3;

    private final CdcInboxRepository cdcInboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Save a CDC event to the inbox
     */
    @Transactional
    public CdcInbox saveEvent(CdcEventPayload payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);

            CdcInbox inboxEvent = CdcInbox.builder()
                    .connectorName(payload.getConnectorName())
                    .entityType(payload.getEntityType())
                    .entityId(payload.getEntityId())
                    .operation(payload.getOperation())
                    .payload(payloadJson)
                    .status(CdcInbox.ProcessingStatus.PENDING)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .build();

            CdcInbox saved = cdcInboxRepository.save(inboxEvent);
            log.info("Saved CDC event to inbox: ID={}, EntityType={}, EntityId={}, Operation={}",
                    saved.getId(), saved.getEntityType(), saved.getEntityId(), saved.getOperation());
            return saved;

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize CDC payload for entity: {}", payload.getEntityId(), e);
            throw new RuntimeException("Failed to serialize CDC payload", e);
        }
    }

    /**
     * Find all pending events
     */
    @Transactional(readOnly = true)
    public List<CdcInbox> findPendingEvents() {
        return cdcInboxRepository.findByStatus(CdcInbox.ProcessingStatus.PENDING);
    }

    /**
     * Find pending events for a specific connector
     */
    @Transactional(readOnly = true)
    public List<CdcInbox> findPendingEventsByConnector(String connectorName) {
        return cdcInboxRepository.findByStatusAndConnectorName(CdcInbox.ProcessingStatus.PENDING, connectorName);
    }

    /**
     * Find pending events for a specific entity type
     */
    @Transactional(readOnly = true)
    public List<CdcInbox> findPendingEventsByEntityType(String entityType) {
        return cdcInboxRepository.findByStatusAndEntityType(CdcInbox.ProcessingStatus.PENDING, entityType);
    }

    /**
     * Atomically claim a pending event before processing it, so two application instances
     * (e.g. overlapping pods during a rolling deploy) never process the same event concurrently.
     *
     * @return true if this caller now owns the event, false if another instance already claimed it
     */
    @Transactional
    public boolean tryClaim(UUID eventId) {
        return cdcInboxRepository.claim(eventId, LocalDateTime.now()) == 1;
    }

    /**
     * Mark event as processed
     */
    @Transactional
    public void markAsProcessed(UUID eventId) {
        CdcInbox inboxEvent = cdcInboxRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("CDC Inbox event not found: " + eventId));

        inboxEvent.setStatus(CdcInbox.ProcessingStatus.PROCESSED);
        inboxEvent.setProcessedAt(LocalDateTime.now());
        inboxEvent.setErrorMessage(null);

        cdcInboxRepository.save(inboxEvent);
        log.info("Marked CDC event as processed: ID={}", eventId);
    }

    /**
     * Mark event as failed
     */
    @Transactional
    public void markAsFailed(UUID eventId, String errorMessage) {
        CdcInbox inboxEvent = cdcInboxRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("CDC Inbox event not found: " + eventId));

        inboxEvent.setStatus(CdcInbox.ProcessingStatus.FAILED);
        inboxEvent.setRetryCount(inboxEvent.getRetryCount() + 1);
        inboxEvent.setErrorMessage(errorMessage);

        cdcInboxRepository.save(inboxEvent);

        if (inboxEvent.getRetryCount() >= MAX_RETRIES) {
            // findFailedEventsForRetry excludes this row from here on - no further automatic
            // attempt will ever be made. This line is the only signal that happens; without it,
            // the event just sits in the table forever with nothing surfacing the data loss.
            log.error("CDC event permanently failed after {} retries and will NOT be retried again - "
                            + "requires manual intervention. ID={}, EntityType={}, EntityId={}, Operation={}, Error={}",
                    MAX_RETRIES, eventId, inboxEvent.getEntityType(), inboxEvent.getEntityId(),
                    inboxEvent.getOperation(), errorMessage);
        } else {
            log.error("Marked CDC event as failed: ID={}, RetryCount={}, Error={}",
                    eventId, inboxEvent.getRetryCount(), errorMessage);
        }
    }

    /**
     * Mark failed event for retry (reset to PENDING)
     */
    @Transactional
    public void markForRetry(UUID eventId) {
        CdcInbox inboxEvent = cdcInboxRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("CDC Inbox event not found: " + eventId));

        inboxEvent.setStatus(CdcInbox.ProcessingStatus.PENDING);
        inboxEvent.setErrorMessage(null);

        cdcInboxRepository.save(inboxEvent);
        log.info("Marked CDC event for retry: ID={}, RetryCount={}", eventId, inboxEvent.getRetryCount());
    }

    /**
     * Deserialize payload from JSON
     */
    public CdcEventPayload deserializePayload(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, CdcEventPayload.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize CDC payload", e);
            throw new RuntimeException("Failed to deserialize CDC payload", e);
        }
    }

    /**
     * Get pending event count
     */
    @Transactional(readOnly = true)
    public long getPendingEventCount() {
        return cdcInboxRepository.countByStatus(CdcInbox.ProcessingStatus.PENDING);
    }

    /**
     * Find failed events eligible for retry
     */
    @Transactional(readOnly = true)
    public List<CdcInbox> findFailedEventsForRetry(int maxRetries) {
        return cdcInboxRepository.findByStatusAndRetryCountLessThan(CdcInbox.ProcessingStatus.FAILED, maxRetries);
    }
}
