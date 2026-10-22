package com.AccountReceivableManagement.cdc.service;

import com.AccountReceivableManagement.cdc.parsing.CdcValueConverter;
import com.AccountReceivableManagement.cdc.payload.CdcEventPayload;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity_enums.project.ProjectStatus;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import com.AccountReceivableManagement.service_interface.client.ClientBudgetSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Snapshot ("r") handling for the project processor. Uses the real {@link CdcValueConverter} and an
 * in-memory repository so the PMS id and client UUID mappings are exercised for real.
 */
class ProjectSnapshotProcessingTest {

    private final Map<Long, ProjectMasterReference> store = new HashMap<>();
    private ProjectMasterReferenceRepository repository;
    private ClientBudgetSummaryService budgetService;
    private ProjectDataProcessorImpl processor;
    private UUID clientId;

    @BeforeEach
    void setUp() {
        repository = mock(ProjectMasterReferenceRepository.class);
        budgetService = mock(ClientBudgetSummaryService.class);
        when(repository.findBypmsProjectId(any())).thenAnswer(i -> Optional.ofNullable(store.get(i.<Long>getArgument(0))));
        when(repository.existsBypmsProjectId(any())).thenAnswer(i -> store.containsKey(i.<Long>getArgument(0)));
        when(repository.save(any(ProjectMasterReference.class))).thenAnswer(i -> {
            ProjectMasterReference p = i.getArgument(0);
            store.put(p.getPmsProjectId(), p);
            return p;
        });
        processor = new ProjectDataProcessorImpl(repository, new CdcValueConverter(), budgetService);
        clientId = UUID.randomUUID();
    }

    private Map<String, Object> row(String name, String budget, UUID client) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 44L);
        row.put("name", name);
        row.put("project_key", "PRJ-44");
        row.put("client_id", client.toString());
        row.put("project_budget", budget);
        row.put("project_budget_currency", "USD");
        row.put("status", "ACTIVE");
        return row;
    }

    private CdcEventPayload event(String op, Map<String, Object> before, Map<String, Object> after) {
        return CdcEventPayload.builder()
                .connectorName("PMS").entityType("PMS-PROJECTS").operation(op)
                .entityId("44").before(before).after(after).build();
    }

    @Test
    void snapshotCreatesMissingProjectWithMappedFields() {
        processor.process(event("r", null, row("Alpha", "1000.00", clientId)));

        ProjectMasterReference saved = store.get(44L);
        assertNotNull(saved);
        assertEquals(44L, saved.getPmsProjectId());
        assertEquals("Alpha", saved.getProjectName());
        assertEquals("PRJ-44", saved.getProjectCode());
        assertEquals(clientId, saved.getClientId());
        assertEquals(0, new java.math.BigDecimal("1000.00").compareTo(saved.getProjectBudget()));
        assertEquals("USD", saved.getProjectBudgetCurrency());
        assertEquals(ProjectStatus.ACTIVE, saved.getProjectStatus());
        verify(budgetService, times(1)).refreshClientBudget(clientId);
    }

    @Test
    void snapshotKeepsPlanningAndArchivedStatusesAsReceived() {
        Map<String, Object> planning = row("Alpha", "1000.00", clientId);
        planning.put("status", "PLANNING");
        processor.process(event("r", null, planning));
        assertEquals(ProjectStatus.PLANNING, store.get(44L).getProjectStatus());

        Map<String, Object> archived = row("Alpha", "1000.00", clientId);
        archived.put("status", "ARCHIVED");
        processor.process(event("r", null, archived));
        assertEquals(ProjectStatus.ARCHIVED, store.get(44L).getProjectStatus());
    }

    @Test
    void snapshotUpdatesExistingProjectWithoutCreatingDuplicate() {
        processor.process(event("r", null, row("Alpha", "1000.00", clientId)));

        processor.process(event("r", null, row("Alpha Renamed", "1000.00", clientId)));

        assertEquals(1, store.size());
        assertEquals("Alpha Renamed", store.get(44L).getProjectName());
    }

    @Test
    void repeatedSnapshotIsIdempotentAndRefreshesBudgetOnlyOnce() {
        CdcEventPayload snapshot = event("r", null, row("Alpha", "1000.00", clientId));

        processor.process(snapshot);
        processor.process(snapshot);
        processor.process(snapshot);

        assertEquals(1, store.size());
        verify(repository, times(3)).save(any(ProjectMasterReference.class));
        verify(budgetService, times(1)).refreshClientBudget(clientId);
    }

    @Test
    void snapshotWithChangedBudgetRefreshesAgain() {
        processor.process(event("r", null, row("Alpha", "1000.00", clientId)));

        processor.process(event("r", null, row("Alpha", "2500.00", clientId)));

        verify(budgetService, times(2)).refreshClientBudget(clientId);
    }

    @Test
    void snapshotMovingProjectToAnotherClientRefreshesBothClients() {
        UUID newClient = UUID.randomUUID();
        processor.process(event("r", null, row("Alpha", "1000.00", clientId)));
        reset(budgetService);

        processor.process(event("r", null, row("Alpha", "1000.00", newClient)));

        verify(budgetService).refreshClientBudget(clientId);
        verify(budgetService).refreshClientBudget(newClient);
        assertEquals(newClient, store.get(44L).getClientId());
    }

    @Test
    void snapshotStoresProjectEvenWhenClientIsNotSynchronized() {
        // The client reference is a plain UUID with no FK; the budget refresh fails for an unknown client
        // but must not stop the project from being stored.
        doThrow(new RuntimeException("Client not found.")).when(budgetService).refreshClientBudget(clientId);

        assertDoesNotThrow(() -> processor.process(event("r", null, row("Alpha", "1000.00", clientId))));

        assertEquals(clientId, store.get(44L).getClientId());
    }

    @Test
    void snapshotWithoutClientReferenceSkipsBudgetRefresh() {
        Map<String, Object> after = row("Alpha", "1000.00", clientId);
        after.put("client_id", null);

        processor.process(event("r", null, after));

        assertNotNull(store.get(44L));
        verify(budgetService, never()).refreshClientBudget(any());
    }

    @Test
    void snapshotWithoutAfterDataSavesNothing() {
        processor.process(event("r", null, null));

        verify(repository, never()).save(any());
        verify(budgetService, never()).refreshClientBudget(any());
    }

    @Test
    void snapshotWithoutProjectIdIsRejected() {
        Map<String, Object> after = row("Alpha", "1000.00", clientId);
        after.remove("id");

        assertThrows(IllegalArgumentException.class, () -> processor.process(event("r", null, after)));
        verify(repository, never()).save(any());
    }

    @Test
    void createUpdateAndDeleteKeepWorkingAlongsideSnapshots() {
        processor.process(event("c", null, row("Alpha", "1000.00", clientId)));
        assertEquals("Alpha", store.get(44L).getProjectName());

        processor.process(event("u", null, row("Alpha v2", "1000.00", clientId)));
        assertEquals("Alpha v2", store.get(44L).getProjectName());

        // A later snapshot refreshes the same row rather than creating another one.
        processor.process(event("r", null, row("Alpha v3", "1000.00", clientId)));
        assertEquals(1, store.size());
        assertEquals("Alpha v3", store.get(44L).getProjectName());

        doAnswer(i -> {
            store.remove(i.<ProjectMasterReference>getArgument(0).getPmsProjectId());
            return null;
        }).when(repository).delete(any(ProjectMasterReference.class));
        processor.process(event("d", row("Alpha v3", "1000.00", clientId), null));
        assertFalse(store.containsKey(44L));
    }

    @Test
    void unknownOperationIsStillRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> processor.process(event("x", null, row("Alpha", "1000.00", clientId))));
    }
}
