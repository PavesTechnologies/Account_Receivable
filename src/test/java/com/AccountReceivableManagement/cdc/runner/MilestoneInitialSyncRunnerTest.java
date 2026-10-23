package com.AccountReceivableManagement.cdc.runner;

import com.AccountReceivableManagement.cdc.config.properties.ProjectCdcProperties;
import com.AccountReceivableManagement.cdc.payload.CdcEventPayload;
import com.AccountReceivableManagement.cdc.service.MilestoneDataProcessor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MilestoneInitialSyncRunnerTest {

    @Test
    void everyExistingRowIsAppliedAsSnapshotEventAndFailuresDoNotStopTheRest() {
        MilestoneDataProcessor processor = mock(MilestoneDataProcessor.class);
        JdbcTemplate pms = mock(JdbcTemplate.class);
        when(pms.queryForList(MilestoneInitialSyncRunner.SELECT_MILESTONES)).thenReturn(List.of(
                Map.of("id", 1L, "version", 0L),
                Map.of("id", 2L, "version", 4L),
                Map.of("id", 3L, "version", 1L)));
        doNothing().doThrow(new IllegalStateException("bad row")).doNothing().when(processor).process(any());

        int applied = new MilestoneInitialSyncRunner(new ProjectCdcProperties(), processor).syncAll(pms);

        assertEquals(2, applied);
        ArgumentCaptor<CdcEventPayload> captor = ArgumentCaptor.forClass(CdcEventPayload.class);
        verify(processor, times(3)).process(captor.capture());
        captor.getAllValues().forEach(p -> {
            assertEquals("r", p.getOperation());
            assertEquals("PMS-PROJECT_MILESTONES", p.getEntityType());
        });
        assertEquals("2", captor.getAllValues().get(1).getEntityId());
    }
}
