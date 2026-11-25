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
     * Record that processing this event failed. The event goes back to FAILED, to be picked up by the retry sweep,
     * until {@code maxRetries} attempts are used up; then it becomes {@link CdcInbox.ProcessingStatus#DEAD_LETTER}.
     *
     * <p>The status matters because the retry sweep selects FAILED events with {@code retryCount < maxRetries}: an
     * exhausted event was previously left FAILED, which simply stopped matching that query. Nothing recorded that it had
     * been abandoned, so the mirror row it should have updated stayed stale with no log line, no metric and no way to
     * replay it short of restarting the application (which repairs milestones only as a side effect of the startup
     * re-read). DEAD_LETTER names that state so it can be counted, reported and replayed.</p>
     */
    @Transactional
    public void markAsFailed(UUID eventId, String errorMessage, int maxRetries) {
        CdcInbox inboxEvent = cdcInboxRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("CDC Inbox event not found: " + eventId));

        int attempts = inboxEvent.getRetryCount() + 1;
        inboxEvent.setRetryCount(attempts);
        inboxEvent.setErrorMessage(errorMessage);

        if (attempts >= maxRetries) {
            inboxEvent.setStatus(CdcInbox.ProcessingStatus.DEAD_LETTER);
            cdcInboxRepository.save(inboxEvent);
            log.error("[CDC-INBOX] DEAD LETTER after {} attempt(s) - ID={}, Connector={}, EntityType={}, EntityId={}, "
                            + "Operation={}, Error={}. This event will NOT be retried; {} {} is now stale in AR until "
                            + "it is replayed.",
                    attempts, eventId, inboxEvent.getConnectorName(), inboxEvent.getEntityType(),
                    inboxEvent.getEntityId(), inboxEvent.getOperation(), errorMessage,
                    inboxEvent.getEntityType(), inboxEvent.getEntityId());
            return;
        }

        inboxEvent.setStatus(CdcInbox.ProcessingStatus.FAILED);
        cdcInboxRepository.save(inboxEvent);
        log.error("Marked CDC event as failed: ID={}, RetryCount={}/{}, Error={}",
                eventId, attempts, maxRetries, errorMessage);
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

    /** How many events have exhausted their retries and are waiting to be replayed. */
    @Transactional(readOnly = true)
    public long getDeadLetterCount() {
        return cdcInboxRepository.countByStatus(CdcInbox.ProcessingStatus.DEAD_LETTER);
    }

    /** Dead-lettered events grouped as (connectorName, entityType, count, oldestCreatedAt), for reporting. */
    @Transactional(readOnly = true)
    public List<Object[]> summariseDeadLetters() {
        return cdcInboxRepository.summariseDeadLetters();
    }

    /**
     * Put every dead-lettered event back in the queue: status PENDING and the retry count reset, so the normal
     * processing sweep picks it up within seconds and it gets a fresh set of attempts.
     *
     * <p>This is the repair to run once the cause of the failure is fixed. Before, the only thing that recovered an
     * abandoned event was restarting the application, and then only for milestones, because
     * {@code MilestoneInitialSyncRunner} re-reads PMS at startup - client and project events had no recovery at all.
     * Replaying is safe to run at any time: every processor upserts by primary key and drops events that are not newer
     * than what is already stored, so a replayed event that has since been superseded changes nothing.</p>
     *
     * @return how many events were queued for replay
     */
    @Transactional
    public int replayDeadLetters() {
        List<CdcInbox> deadLettered = cdcInboxRepository.findByStatus(CdcInbox.ProcessingStatus.DEAD_LETTER);
        for (CdcInbox event : deadLettered) {
            event.setStatus(CdcInbox.ProcessingStatus.PENDING);
            event.setRetryCount(0);
            event.setErrorMessage(null);
            cdcInboxRepository.save(event);
            log.info("[CDC-INBOX] Replaying dead-lettered event - ID={}, EntityType={}, EntityId={}",
                    event.getId(), event.getEntityType(), event.getEntityId());
        }
        if (!deadLettered.isEmpty()) {
            log.warn("[CDC-INBOX] Queued {} dead-lettered event(s) for replay", deadLettered.size());
        }
        return deadLettered.size();
    }
}
