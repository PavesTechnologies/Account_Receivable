package com.AccountReceivableManagement.cdc.service;

import com.AccountReceivableManagement.cdc.entity.CdcInbox;
import com.AccountReceivableManagement.cdc.repository.CdcInboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Retry exhaustion in the CDC inbox.
 *
 * <p>The retry sweep selects FAILED events with {@code retryCount < maxRetries}. An exhausted event used to be left
 * FAILED, which simply stopped matching that query: it was never retried and nothing recorded that it had been
 * abandoned, so the AR mirror stayed stale in silence. DEAD_LETTER names that state so it can be counted, reported and
 * replayed.</p>
 */
class CdcInboxDeadLetterTest {

    private static final int MAX_RETRIES = 3;

    private CdcInboxRepository repository;
    private CdcInboxService service;

    @BeforeEach
    void setUp() {
        repository = mock(CdcInboxRepository.class);
        service = new CdcInboxService(repository, new ObjectMapper());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private CdcInbox event(int retryCount, CdcInbox.ProcessingStatus status) {
        CdcInbox inbox = CdcInbox.builder()
                .id(UUID.randomUUID())
                .connectorName("project-cdc")
                .entityType("PMS-PROJECT_MILESTONES")
                .entityId("42")
                .operation("u")
                .payload("{}")
                .status(status)
                .retryCount(retryCount)
                .build();
        when(repository.findById(inbox.getId())).thenReturn(Optional.of(inbox));
        return inbox;
    }

    @Test
    void failureBelowTheLimitStaysRetryable() {
        CdcInbox inbox = event(0, CdcInbox.ProcessingStatus.PENDING);

        service.markAsFailed(inbox.getId(), "boom", MAX_RETRIES);

        assertEquals(CdcInbox.ProcessingStatus.FAILED, inbox.getStatus());
        assertEquals(1, inbox.getRetryCount());
    }

    /** The attempt that uses up the budget dead-letters the event instead of leaving it silently unretryable. */
    @Test
    void lastAttemptDeadLettersTheEvent() {
        CdcInbox inbox = event(MAX_RETRIES - 1, CdcInbox.ProcessingStatus.FAILED);

        service.markAsFailed(inbox.getId(), "still broken", MAX_RETRIES);

        assertEquals(CdcInbox.ProcessingStatus.DEAD_LETTER, inbox.getStatus());
        assertEquals(MAX_RETRIES, inbox.getRetryCount());
        assertEquals("still broken", inbox.getErrorMessage());
    }

    /** A dead-lettered event is never selected by the retry sweep, so it cannot churn. */
    @Test
    void deadLetteredEventIsNotPickedUpByTheRetrySweep() {
        when(repository.findByStatusAndRetryCountLessThan(CdcInbox.ProcessingStatus.FAILED, MAX_RETRIES))
                .thenReturn(List.of());

        assertEquals(0, service.findFailedEventsForRetry(MAX_RETRIES).size());
    }

    @Test
    void replayQueuesDeadLettersWithAFreshBudget() {
        CdcInbox inbox = event(MAX_RETRIES, CdcInbox.ProcessingStatus.DEAD_LETTER);
        inbox.setErrorMessage("still broken");
        when(repository.findByStatus(CdcInbox.ProcessingStatus.DEAD_LETTER)).thenReturn(List.of(inbox));

        int queued = service.replayDeadLetters();

        assertEquals(1, queued);
        assertEquals(CdcInbox.ProcessingStatus.PENDING, inbox.getStatus());
        assertEquals(0, inbox.getRetryCount());
        assertNull(inbox.getErrorMessage());
    }

    @Test
    void replayWithNothingAbandonedIsANoOp() {
        when(repository.findByStatus(CdcInbox.ProcessingStatus.DEAD_LETTER)).thenReturn(List.of());

        assertEquals(0, service.replayDeadLetters());
    }
}
