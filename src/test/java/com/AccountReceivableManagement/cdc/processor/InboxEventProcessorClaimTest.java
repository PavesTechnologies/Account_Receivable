package com.AccountReceivableManagement.cdc.processor;

import com.AccountReceivableManagement.cdc.entity.CdcInbox;
import com.AccountReceivableManagement.cdc.payload.CdcEventPayload;
import com.AccountReceivableManagement.cdc.service.CdcInboxService;
import com.AccountReceivableManagement.cdc.service.ClientDataProcessor;
import com.AccountReceivableManagement.cdc.service.MilestoneDataProcessor;
import com.AccountReceivableManagement.cdc.service.ProjectDataProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Regression test: this app's rollingUpdate strategy (maxSurge=1, maxUnavailable=0) guarantees two
 * pods run briefly together on every deploy, each polling the same cdc_inbox table every 5s. Without
 * an atomic claim, both could fetch and process the same PENDING row. CdcInboxService.tryClaim()
 * (an atomic conditional UPDATE) must gate processing so a lost race is skipped, not double-processed.
 */
@ExtendWith(MockitoExtension.class)
class InboxEventProcessorClaimTest {

    @Mock
    private CdcInboxService cdcInboxService;
    @Mock
    private ClientDataProcessor clientDataProcessor;
    @Mock
    private ProjectDataProcessor projectDataProcessor;
    @Mock
    private MilestoneDataProcessor milestoneDataProcessor;

    private InboxEventProcessor processor;
    private CdcInbox event;

    @BeforeEach
    void setUp() {
        processor = new InboxEventProcessor(cdcInboxService, clientDataProcessor, projectDataProcessor,
                milestoneDataProcessor);

        event = CdcInbox.builder()
                .id(UUID.randomUUID())
                .connectorName("project-cdc")
                .entityType("PMS-PROJECTS")
                .entityId("35")
                .operation("c")
                .payload("{}")
                .status(CdcInbox.ProcessingStatus.PENDING)
                .build();

        when(cdcInboxService.findPendingEvents()).thenReturn(List.of(event));
    }

    @Test
    void losingTheClaimSkipsProcessingEntirely() {
        when(cdcInboxService.tryClaim(event.getId())).thenReturn(false);

        processor.processPendingEvents();

        verify(cdcInboxService, never()).deserializePayload(any());
        verify(projectDataProcessor, never()).process(any());
        verify(cdcInboxService, never()).markAsProcessed(any());
        verify(cdcInboxService, never()).markAsFailed(any(), any());
    }

    @Test
    void winningTheClaimProcessesNormally() {
        when(cdcInboxService.tryClaim(event.getId())).thenReturn(true);
        CdcEventPayload payload = CdcEventPayload.builder()
                .entityType("PMS-PROJECTS")
                .operation("c")
                .build();
        when(cdcInboxService.deserializePayload("{}")).thenReturn(payload);

        processor.processPendingEvents();

        verify(projectDataProcessor).process(payload);
        verify(cdcInboxService).markAsProcessed(event.getId());
    }
}
