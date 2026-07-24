package com.AccountReceivableManagement.CDC.service;

import com.AccountReceivableManagement.CDC.entity.CdcInbox;
import com.AccountReceivableManagement.CDC.payload.CdcEventPayload;
import com.AccountReceivableManagement.CDC.repository.CdcInboxRepository;
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
        log.error("Marked CDC event as failed: ID={}, RetryCount={}, Error={}",
                eventId, inboxEvent.getRetryCount(), errorMessage);
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
