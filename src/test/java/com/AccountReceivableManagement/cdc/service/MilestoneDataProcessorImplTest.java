package com.AccountReceivableManagement.cdc.service;

import com.AccountReceivableManagement.cdc.parsing.CdcValueConverter;
import com.AccountReceivableManagement.cdc.payload.CdcEventPayload;
import com.AccountReceivableManagement.entity.project_entity.ProjectMilestoneReference;
import com.AccountReceivableManagement.repo.project.ProjectMilestoneReferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MilestoneDataProcessorImplTest {

    private ProjectMilestoneReferenceRepository repository;
    private MilestoneDataProcessorImpl processor;

    @BeforeEach
    void setUp() {
        repository = mock(ProjectMilestoneReferenceRepository.class);
        // Real converter: verifies Debezium value shapes (epoch-day dates, string decimals, bit booleans).
        processor = new MilestoneDataProcessorImpl(repository, new CdcValueConverter());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /** A row as Debezium delivers it (decimal.handling.mode=string, DATE as epoch days). */
    private static Map<String, Object> debeziumRow(long version) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", 42L);
        data.put("project_id", 10L);
        data.put("epic_id", null);
        data.put("milestone_code", "PAY-M001");
        data.put("name", "UAT Complete");
        data.put("sequence_order", 3);
        data.put("planned_due_date", (int) LocalDate.of(2026, 11, 30).toEpochDay());
        data.put("actual_completion_date", null);
        data.put("execution_status", "IN_PROGRESS");
        data.put("billable", true);
        data.put("allocation_percentage", "25.50");
        data.put("billing_readiness", "NOT_READY");
        data.put("version", version);
        data.put("created_by", 7L);
        return data;
    }

    private static CdcEventPayload event(String op, Map<String, Object> before, Map<String, Object> after) {
        return CdcEventPayload.builder()
                .connectorName("project-cdc")
                .entityType("PMS-PROJECT_MILESTONES")
                .entityId("42")
                .operation(op)
                .before(before)
                .after(after)
                .build();
    }

    private ProjectMilestoneReference savedReference() {
        ArgumentCaptor<ProjectMilestoneReference> captor = ArgumentCaptor.forClass(ProjectMilestoneReference.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void newMilestoneIsCreatedWithAllContractFields() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        processor.process(event("c", null, debeziumRow(0)));

        ProjectMilestoneReference saved = savedReference();
        assertEquals(42L, saved.getPmsMilestoneId());
        assertEquals(10L, saved.getPmsProjectId());
        assertEquals("PAY-M001", saved.getMilestoneCode());
        assertEquals("UAT Complete", saved.getMilestoneName());
        assertEquals(LocalDate.of(2026, 11, 30), saved.getPlannedDueDate());
        assertEquals("IN_PROGRESS", saved.getExecutionStatus());
        assertTrue(saved.getBillable());
        assertEquals(0, new BigDecimal("25.50").compareTo(saved.getAllocationPercentage()));
        assertEquals("NOT_READY", saved.getBillingReadiness());
        assertEquals(0L, saved.getSourceVersion());
        assertFalse(saved.getSourceDeleted());
        assertNotNull(saved.getLastSyncedAt());
    }

    @Test
    void snapshotReadIsTreatedAsUpsert() {
        when(repository.findById(42L)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> processor.process(event("r", null, debeziumRow(4))));
        assertEquals(4L, savedReference().getSourceVersion());
    }

    @Test
    void initialSyncRowWithJdbcTypesIsMapped() {
        // Shape returned by JdbcTemplate.queryForList on the PMS table.
        Map<String, Object> jdbcRow = new HashMap<>();
        jdbcRow.put("id", 42L);
        jdbcRow.put("project_id", 9L);
        jdbcRow.put("milestone_code", "A-709-M002");
        jdbcRow.put("name", "Go-live");
        jdbcRow.put("planned_due_date", Date.valueOf("2026-10-16"));
        jdbcRow.put("actual_completion_date", Date.valueOf("2026-10-16"));
        jdbcRow.put("execution_status", "COMPLETED");
        jdbcRow.put("billable", Boolean.TRUE);
        jdbcRow.put("allocation_percentage", new BigDecimal("80.00"));
        jdbcRow.put("billing_readiness", "READY_FOR_BILLING");
        jdbcRow.put("version", 3L);
        jdbcRow.put("created_at", LocalDateTime.of(2026, 10, 16, 10, 0));
        when(repository.findById(42L)).thenReturn(Optional.empty());

        processor.process(event("r", null, jdbcRow));

        ProjectMilestoneReference saved = savedReference();
        assertEquals(9L, saved.getPmsProjectId());
        assertEquals(LocalDate.of(2026, 10, 16), saved.getActualCompletionDate());
        assertEquals("READY_FOR_BILLING", saved.getBillingReadiness());
        assertEquals(0, new BigDecimal("80.00").compareTo(saved.getAllocationPercentage()));
        assertEquals(LocalDateTime.of(2026, 10, 16, 10, 0), saved.getCreatedAt());
    }

    @Test
    void newerUpdateOverwritesExistingRow() {
        ProjectMilestoneReference existing = ProjectMilestoneReference.builder()
                .pmsMilestoneId(42L).sourceVersion(1L).executionStatus("PLANNED").build();
        when(repository.findById(42L)).thenReturn(Optional.of(existing));

        Map<String, Object> after = debeziumRow(2);
        after.put("execution_status", "COMPLETED");
        after.put("billing_readiness", "READY_FOR_BILLING");
        processor.process(event("u", null, after));

        ProjectMilestoneReference saved = savedReference();
        assertSame(existing, saved);
        assertEquals("COMPLETED", saved.getExecutionStatus());
        assertEquals("READY_FOR_BILLING", saved.getBillingReadiness());
        assertEquals(2L, saved.getSourceVersion());
    }

    @Test
    void staleOrReplayedEventIsIgnored() {
        ProjectMilestoneReference existing = ProjectMilestoneReference.builder()
                .pmsMilestoneId(42L).sourceVersion(5L).executionStatus("COMPLETED").build();
        when(repository.findById(42L)).thenReturn(Optional.of(existing));

        processor.process(event("u", null, debeziumRow(5)));
        processor.process(event("r", null, debeziumRow(3)));

        verify(repository, never()).save(any());
        assertEquals("COMPLETED", existing.getExecutionStatus());
    }

    @Test
    void deleteKeepsTombstoneForTraceability() {
        ProjectMilestoneReference existing = ProjectMilestoneReference.builder()
                .pmsMilestoneId(42L).sourceVersion(1L).sourceDeleted(false).build();
        when(repository.findById(42L)).thenReturn(Optional.of(existing));

        processor.process(event("d", debeziumRow(1), null));

        assertTrue(savedReference().getSourceDeleted());
        verify(repository, never()).delete(any());
    }

    @Test
    void lateCreateAfterDeleteDoesNotResurrect() {
        ProjectMilestoneReference tombstone = ProjectMilestoneReference.builder()
                .pmsMilestoneId(42L).sourceDeleted(true).build();
        when(repository.findById(42L)).thenReturn(Optional.of(tombstone));

        processor.process(event("c", null, debeziumRow(0)));

        verify(repository, never()).save(any());
        assertTrue(tombstone.getSourceDeleted());
    }

    @Test
    void unknownOperationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> processor.process(event("x", null, debeziumRow(0))));
    }
}
