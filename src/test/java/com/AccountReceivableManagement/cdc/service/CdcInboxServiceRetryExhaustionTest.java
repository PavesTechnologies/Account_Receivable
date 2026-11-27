package com.AccountReceivableManagement.cdc.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.AccountReceivableManagement.cdc.entity.CdcInbox;
import com.AccountReceivableManagement.cdc.repository.CdcInboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for the "retry exhaustion is silent" gap: once an event's retryCount reaches
 * CdcInboxService.MAX_RETRIES, InboxEventProcessor.retryFailedEvents() excludes it forever - no
 * further automatic attempt is ever made. Before this fix, that moment produced the exact same
 * log line as any other (recoverable) failure, so there was no signal that the system had given
 * up permanently. markAsFailed must now log a distinct, loud line the instant the ceiling is hit.
 */
class CdcInboxServiceRetryExhaustionTest {

    private CdcInboxRepository repository;
    private CdcInboxService service;
    private ListAppender<ILoggingEvent> appender;
    private Logger root;
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        repository = mock(CdcInboxRepository.class);
        service = new CdcInboxService(repository, new ObjectMapper());

        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        previousLevel = root.getLevel();
        root.setLevel(Level.ALL);
        appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        root.detachAppender(appender);
        root.setLevel(previousLevel);
    }

    private String captured() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    private CdcInbox eventWithRetryCount(int retryCount) {
        return CdcInbox.builder()
                .id(UUID.randomUUID())
                .connectorName("project-cdc")
                .entityType("PMS-PROJECTS")
                .entityId("35")
                .operation("u")
                .payload("{}")
                .status(CdcInbox.ProcessingStatus.FAILED)
                .retryCount(retryCount)
                .build();
    }

    @Test
    void belowCeilingLogsOnlyTheOrdinaryFailureMessage() {
        CdcInbox event = eventWithRetryCount(1); // becomes 2 after this failure, still < MAX_RETRIES (3)
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.markAsFailed(event.getId(), "transient error");

        assertThat(captured()).contains("Marked CDC event as failed");
        assertThat(captured()).doesNotContain("permanently failed").doesNotContain("manual intervention");
    }

    @Test
    void reachingTheCeilingLogsTheExhaustionAlertInstead() {
        CdcInbox event = eventWithRetryCount(CdcInboxService.MAX_RETRIES - 1); // becomes MAX_RETRIES after this failure
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.markAsFailed(event.getId(), "persistent error");

        assertThat(captured()).contains("permanently failed").contains("manual intervention")
                .contains(String.valueOf(CdcInboxService.MAX_RETRIES));
        assertThat(captured()).doesNotContain("Marked CDC event as failed:");
    }
}
