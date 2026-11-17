package com.AccountReceivableManagement.service_Imple.automatic_billing;

import com.AccountReceivableManagement.config.properties.AutomaticBillingSchedulerProperties;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingPeriodDto;
import com.AccountReceivableManagement.entity.automatic_billing.AutomaticBillingRun;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingStep;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.automatic_billing.AutomaticBillingRunRepository;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.service_Imple.projectbilling_config.BillingOccurrenceServiceImpl;
import com.AccountReceivableManagement.service_interface.automatic_billing.AutomaticBillingRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Batch and discovery logic of AutomaticBillingScheduler, with every collaborator mocked. */
@ExtendWith(MockitoExtension.class)
class AutomaticBillingSchedulerTest {

    /** Processing date: 01-Nov-2026. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T06:00:00Z"), ZoneId.of("UTC"));
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    @Mock private AutomaticBillingOrchestrator orchestrator;
    @Mock private AutomaticBillingRunService runService;
    @Mock private AutomaticBillingRunRepository runRepository;
    @Mock private BillingScheduleRepository scheduleRepository;
    @Mock private BillingConfigurationRepository configurationRepository;
    @Mock private BillingSnapshotRepository snapshotRepository;
    @Mock private BillingOccurrenceServiceImpl occurrenceService;
    @Mock private PlatformTransactionManager transactionManager;

    private AutomaticBillingSchedulerProperties properties;
    private AutomaticBillingScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new AutomaticBillingSchedulerProperties();
        properties.setEnabled(true);
        properties.setBatchSize(5);
        scheduler = new AutomaticBillingScheduler(properties, orchestrator, runService, runRepository,
                scheduleRepository, configurationRepository, snapshotRepository, occurrenceService,
                transactionManager, CLOCK);

        lenient().when(runRepository.findRunIdsByStatus(any(), any())).thenReturn(List.of());
        lenient().when(scheduleRepository.findDueOccurrenceIdsWithoutAutomaticRun(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(configurationRepository.findAutomaticConfigurationIdsByBillingTypeNamesDueBetween(
                any(), any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(snapshotRepository.findPeriodsByProjectIdEndingOnOrAfter(any(), any())).thenReturn(List.of());
    }

    private AutomaticBillingRun run(UUID id, AutomaticBillingRunStatus status) {
        return AutomaticBillingRun.builder().automaticBillingRunId(id).status(status).build();
    }

    private UUID occurrenceWithRun(AutomaticBillingRunStatus outcome) {
        UUID occurrenceId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        lenient().when(runService.findOrCreateForOccurrence(occurrenceId)).thenReturn(run(runId, AutomaticBillingRunStatus.SCHEDULED));
        lenient().when(orchestrator.processRun(runId)).thenReturn(run(runId, outcome));
        return occurrenceId;
    }

    // ================================================================ configuration

    @Test
    void disabled_touchesNothing() {
        properties.setEnabled(false);

        AutomaticBillingScheduler.TickSummary summary = scheduler.runOnce();

        assertThat(summary.handled()).isZero();
        verifyNoInteractions(orchestrator, runService, runRepository, scheduleRepository, configurationRepository,
                snapshotRepository, occurrenceService);
    }

    @Test
    void scheduledTick_whenDisabled_alsoDoesNothing() {
        properties.setEnabled(false);

        scheduler.scheduledTick();

        verifyNoInteractions(orchestrator, scheduleRepository);
    }

    @Test
    void batchSize_boundsEveryQueryAndTheTotal() {
        properties.setBatchSize(3);
        UUID pending = UUID.randomUUID();
        when(runRepository.findRunIdsByStatus(eq(AutomaticBillingRunStatus.SCHEDULED), any())).thenReturn(List.of(pending));
        when(orchestrator.processRun(pending)).thenReturn(run(pending, AutomaticBillingRunStatus.PENDING_APPROVAL));
        List<UUID> occurrences = List.of(occurrenceWithRun(AutomaticBillingRunStatus.PENDING_APPROVAL),
                occurrenceWithRun(AutomaticBillingRunStatus.PENDING_APPROVAL));
        when(scheduleRepository.findDueOccurrenceIdsWithoutAutomaticRun(any(), any(), any(), any(), any(), any()))
                .thenReturn(occurrences);

        AutomaticBillingScheduler.TickSummary summary = scheduler.runOnce();

        ArgumentCaptor<Pageable> runPage = ArgumentCaptor.forClass(Pageable.class);
        verify(runRepository).findRunIdsByStatus(eq(AutomaticBillingRunStatus.SCHEDULED), runPage.capture());
        assertThat(runPage.getValue().getPageSize()).isEqualTo(3);
        ArgumentCaptor<Pageable> occurrencePage = ArgumentCaptor.forClass(Pageable.class);
        verify(scheduleRepository).findDueOccurrenceIdsWithoutAutomaticRun(eq(TODAY),
                eq(AutomaticBillingScheduler.UNBOUNDED_EARLIEST_BILLING_DATE), eq(ApprovalStatus.APPROVED),
                eq(InvoiceGenerationType.AUTOMATIC), eq(BillingSchedule.UNBILLED_STATUSES), occurrencePage.capture());
        assertThat(occurrencePage.getValue().getPageSize()).isEqualTo(2);
        // Batch full: T&M discovery is not even started.
        verifyNoInteractions(configurationRepository);
        assertThat(summary.handled()).isEqualTo(3);
    }

    // ================================================================ processing

    @Test
    void dueOccurrences_getARunAndAreHandedToTheOrchestrator() {
        UUID first = occurrenceWithRun(AutomaticBillingRunStatus.PENDING_APPROVAL);
        UUID second = occurrenceWithRun(AutomaticBillingRunStatus.PENDING_APPROVAL);
        when(scheduleRepository.findDueOccurrenceIdsWithoutAutomaticRun(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(first, second));

        AutomaticBillingScheduler.TickSummary summary = scheduler.runOnce();

        verify(runService).findOrCreateForOccurrence(first);
        verify(runService).findOrCreateForOccurrence(second);
        verify(orchestrator, times(2)).processRun(any());
        assertThat(summary.newOccurrences()).isEqualTo(2);
        assertThat(summary.outcome(AutomaticBillingRunStatus.PENDING_APPROVAL)).isEqualTo(2);
    }

    @Test
    void scheduledStragglerRuns_areResumedFirst() {
        UUID straggler = UUID.randomUUID();
        when(runRepository.findRunIdsByStatus(eq(AutomaticBillingRunStatus.SCHEDULED), any())).thenReturn(List.of(straggler));
        when(orchestrator.processRun(straggler)).thenReturn(run(straggler, AutomaticBillingRunStatus.PENDING_APPROVAL));

        AutomaticBillingScheduler.TickSummary summary = scheduler.runOnce();

        verify(orchestrator).processRun(straggler);
        verify(runService, never()).findOrCreateForOccurrence(any());
        assertThat(summary.resumed()).isEqualTo(1);
    }

    @Test
    void failedRuns_areOnlyRetriedThroughTheirScheduledRetry() {
        scheduler.runOnce();

        verify(runRepository).findRunIdsByStatus(eq(AutomaticBillingRunStatus.SCHEDULED), any());
        verify(runRepository, never()).findRunIdsByStatus(eq(AutomaticBillingRunStatus.FAILED), any());
        verify(runRepository).findRunIdsDueForRetry(eq(LocalDateTime.now(CLOCK)), any());
    }

    // ================================================================ failure isolation

    @Test
    void oneFailingBillingEvent_doesNotStopTheRest() {
        UUID failsInProcessing = UUID.randomUUID();
        UUID failingRun = UUID.randomUUID();
        when(runService.findOrCreateForOccurrence(failsInProcessing)).thenReturn(run(failingRun, AutomaticBillingRunStatus.SCHEDULED));
        when(orchestrator.processRun(failingRun)).thenThrow(new RuntimeException("database connection reset"));
        UUID rejectedAtCreation = UUID.randomUUID();
        when(runService.findOrCreateForOccurrence(rejectedAtCreation))
                .thenThrow(new GlobalExceptionHandler.ValidationException("not due"));
        UUID recordsFailure = occurrenceWithRun(AutomaticBillingRunStatus.FAILED);
        UUID succeeds = occurrenceWithRun(AutomaticBillingRunStatus.PENDING_APPROVAL);
        when(scheduleRepository.findDueOccurrenceIdsWithoutAutomaticRun(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(failsInProcessing, rejectedAtCreation, recordsFailure, succeeds));

        AutomaticBillingScheduler.TickSummary summary = scheduler.runOnce();

        verify(runService).findOrCreateForOccurrence(succeeds);
        assertThat(summary.errors()).isEqualTo(2);
        assertThat(summary.outcome(AutomaticBillingRunStatus.FAILED)).isEqualTo(1);
        assertThat(summary.outcome(AutomaticBillingRunStatus.PENDING_APPROVAL)).isEqualTo(1);
    }

    @Test
    void discoveryFailure_isContainedInTheScheduledTick() {
        when(runRepository.findRunIdsByStatus(any(), any())).thenThrow(new RuntimeException("db down"));

        scheduler.scheduledTick();

        verifyNoInteractions(orchestrator);
    }

    // ================================================================ T&M

    private UUID timeAndMaterialConfiguration(List<BillingPeriodDto> periods) {
        UUID configurationId = UUID.randomUUID();
        when(configurationRepository.findAutomaticConfigurationIdsByBillingTypeNamesDueBetween(
                eq(ApprovalStatus.APPROVED), eq(InvoiceGenerationType.AUTOMATIC),
                eq(BillingOccurrenceServiceImpl.TIME_AND_MATERIAL_BILLING_TYPE_NAMES), any(), eq(TODAY)))
                .thenReturn(List.of(configurationId));
        when(configurationRepository.findById(configurationId)).thenReturn(Optional.of(BillingConfiguration.builder()
                .billingConfigurationId(configurationId)
                .project(ProjectMasterReference.builder().pmsProjectId(23L).build())
                .build()));
        when(occurrenceService.resolveTimeAndMaterialBillingPeriods(configurationId, TODAY)).thenReturn(periods);
        return configurationId;
    }

    private BillingPeriodDto period(LocalDate start, LocalDate end) {
        return BillingPeriodDto.builder().periodStartDate(start).periodEndDate(end).billingDate(end).build();
    }

    @Test
    void timeAndMaterial_duePeriodsComeFromTheResolver_futureOnesAreSkipped() {
        UUID configurationId = timeAndMaterialConfiguration(List.of(
                period(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 14)),
                period(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 14))));
        UUID runId = UUID.randomUUID();
        when(runService.findOrCreateForTimeAndMaterialPeriod(configurationId,
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 14)))
                .thenReturn(run(runId, AutomaticBillingRunStatus.SCHEDULED));
        when(orchestrator.processRun(runId)).thenReturn(run(runId, AutomaticBillingRunStatus.PENDING_APPROVAL));

        AutomaticBillingScheduler.TickSummary summary = scheduler.runOnce();

        verify(runService).findOrCreateForTimeAndMaterialPeriod(configurationId,
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 14));
        verify(runService, never()).findOrCreateForTimeAndMaterialPeriod(configurationId,
                LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 14));
        assertThat(summary.newTimeAndMaterialPeriods()).isEqualTo(1);
    }

    @Test
    void timeAndMaterial_periodsWithARunOrAnOverlappingSnapshot_areSkipped() {
        LocalDate augStart = LocalDate.of(2026, 8, 15);
        LocalDate augEnd = LocalDate.of(2026, 9, 14);
        LocalDate sepStart = LocalDate.of(2026, 9, 15);
        LocalDate sepEnd = LocalDate.of(2026, 10, 14);
        UUID configurationId = timeAndMaterialConfiguration(List.of(period(augStart, augEnd), period(sepStart, sepEnd)));
        AutomaticBillingRun augustRun = run(UUID.randomUUID(), AutomaticBillingRunStatus.FAILED);
        augustRun.setBillingPeriodStart(augStart);
        augustRun.setBillingPeriodEnd(augEnd);
        augustRun.setUpdatedAt(LocalDateTime.now(CLOCK)); // attempted today
        when(runRepository.findAllByBillingConfiguration_BillingConfigurationId(configurationId))
                .thenReturn(List.of(augustRun));
        when(snapshotRepository.findPeriodsByProjectIdEndingOnOrAfter(eq(23L), any()))
                .thenReturn(List.<Object[]>of(new Object[]{sepStart.plusDays(3), sepStart.plusDays(10)}));

        AutomaticBillingScheduler.TickSummary summary = scheduler.runOnce();

        verify(runService, never()).findOrCreateForTimeAndMaterialPeriod(any(), any(), any());
        verifyNoInteractions(orchestrator);
        assertThat(summary.handled()).isZero();
    }

    @Test
    void timeAndMaterial_failedSourceAcquisitionFromEarlierDay_isRediscoveredForLateApprovedTimesheets() {
        LocalDate augStart = LocalDate.of(2026, 8, 15);
        LocalDate augEnd = LocalDate.of(2026, 9, 14);
        UUID configurationId = timeAndMaterialConfiguration(List.of(period(augStart, augEnd)));
        UUID runId = UUID.randomUUID();
        AutomaticBillingRun failedRun = run(runId, AutomaticBillingRunStatus.FAILED);
        // Failed yesterday with no snapshot or invoice
        failedRun.setUpdatedAt(LocalDateTime.now(CLOCK).minusDays(1));
        failedRun.setBillingPeriodStart(augStart);
        failedRun.setBillingPeriodEnd(augEnd);
        when(runRepository.findAllByBillingConfiguration_BillingConfigurationId(configurationId))
                .thenReturn(List.of(failedRun));
        when(runService.findOrCreateForTimeAndMaterialPeriod(configurationId, augStart, augEnd))
                .thenReturn(failedRun);
        when(orchestrator.processRun(runId)).thenReturn(run(runId, AutomaticBillingRunStatus.PENDING_APPROVAL));

        AutomaticBillingScheduler.TickSummary summary = scheduler.runOnce();

        verify(runService).findOrCreateForTimeAndMaterialPeriod(configurationId, augStart, augEnd);
        verify(orchestrator).processRun(runId);
        assertThat(summary.newTimeAndMaterialPeriods()).isEqualTo(1);
    }

    // G-29: oldest first across configurations, never-attempted periods before
    // daily re-attempts, a fixed number of queries per configuration.
    @Test
    void timeAndMaterial_oldestFirstAcrossConfigurations_newWorkBeforeRediscoveries_boundedQueries() {
        properties.setBatchSize(2);
        UUID older = UUID.randomUUID();
        UUID newer = UUID.randomUUID();
        when(configurationRepository.findAutomaticConfigurationIdsByBillingTypeNamesDueBetween(
                any(), any(), any(), any(), any())).thenReturn(List.of(newer, older));
        for (UUID id : List.of(older, newer)) {
            when(configurationRepository.findById(id)).thenReturn(Optional.of(BillingConfiguration.builder()
                    .billingConfigurationId(id)
                    .project(ProjectMasterReference.builder().pmsProjectId(23L).build()).build()));
        }
        // "newer" has many historical periods: one failed yesterday (rediscovery) and one never attempted.
        List<BillingPeriodDto> newerPeriods = new java.util.ArrayList<>();
        for (int month = 1; month <= 9; month++) {
            newerPeriods.add(period(LocalDate.of(2026, month, 1), LocalDate.of(2026, month, 1).plusMonths(1).minusDays(1)));
        }
        when(occurrenceService.resolveTimeAndMaterialBillingPeriods(newer, TODAY)).thenReturn(newerPeriods);
        java.util.List<AutomaticBillingRun> newerRuns = new java.util.ArrayList<>();
        for (int month = 1; month <= 8; month++) {
            AutomaticBillingRun done = run(UUID.randomUUID(), AutomaticBillingRunStatus.COMPLETED);
            done.setBillingPeriodStart(LocalDate.of(2026, month, 1));
            done.setBillingPeriodEnd(LocalDate.of(2026, month, 1).plusMonths(1).minusDays(1));
            newerRuns.add(done);
        }
        AutomaticBillingRun failedJanuary = newerRuns.get(0);
        failedJanuary.setStatus(AutomaticBillingRunStatus.FAILED);
        failedJanuary.setUpdatedAt(LocalDateTime.now(CLOCK).minusDays(1));
        when(runRepository.findAllByBillingConfiguration_BillingConfigurationId(newer)).thenReturn(newerRuns);
        // "older" has one never-attempted period ending before the newer one's September.
        when(occurrenceService.resolveTimeAndMaterialBillingPeriods(older, TODAY))
                .thenReturn(List.of(period(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))));
        when(runService.findOrCreateForTimeAndMaterialPeriod(any(), any(), any()))
                .thenAnswer(invocation -> run(UUID.randomUUID(), AutomaticBillingRunStatus.SCHEDULED));
        when(orchestrator.processRun(any())).thenAnswer(invocation ->
                run(invocation.getArgument(0), AutomaticBillingRunStatus.PENDING_APPROVAL));

        scheduler.runOnce();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(runService);
        order.verify(runService).findOrCreateForTimeAndMaterialPeriod(older,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        order.verify(runService).findOrCreateForTimeAndMaterialPeriod(newer,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        // Batch of 2 is full of never-attempted work; January's daily re-attempt waits.
        verify(runService, never()).findOrCreateForTimeAndMaterialPeriod(newer,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        // One run query per configuration, whatever its number of periods.
        verify(runRepository, times(1)).findAllByBillingConfiguration_BillingConfigurationId(newer);
        verify(runRepository, never()).findByBillingConfiguration_BillingConfigurationIdAndBillingPeriodStartAndBillingPeriodEnd(
                any(), any(), any());
        verify(snapshotRepository, never()).existsByProjectIdAndBillingPeriodStartLessThanEqualAndBillingPeriodEndGreaterThanEqual(
                any(), any(), any());
    }

    @Test
    void timeAndMaterial_configurationThatCannotBeResolved_isIsolated() {
        UUID broken = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        when(configurationRepository.findAutomaticConfigurationIdsByBillingTypeNamesDueBetween(
                any(), any(), any(), any(), any())).thenReturn(List.of(broken, healthy));
        when(configurationRepository.findById(broken)).thenReturn(Optional.of(BillingConfiguration.builder()
                .billingConfigurationId(broken)
                .project(ProjectMasterReference.builder().pmsProjectId(1L).build()).build()));
        when(configurationRepository.findById(healthy)).thenReturn(Optional.of(BillingConfiguration.builder()
                .billingConfigurationId(healthy)
                .project(ProjectMasterReference.builder().pmsProjectId(2L).build()).build()));
        when(occurrenceService.resolveTimeAndMaterialBillingPeriods(broken, TODAY))
                .thenThrow(new GlobalExceptionHandler.ValidationException("Billing Frequency is required"));
        when(occurrenceService.resolveTimeAndMaterialBillingPeriods(healthy, TODAY))
                .thenReturn(List.of(period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))));
        UUID runId = UUID.randomUUID();
        when(runService.findOrCreateForTimeAndMaterialPeriod(eq(healthy), any(), any()))
                .thenReturn(run(runId, AutomaticBillingRunStatus.SCHEDULED));
        when(orchestrator.processRun(runId)).thenReturn(run(runId, AutomaticBillingRunStatus.PENDING_APPROVAL));

        AutomaticBillingScheduler.TickSummary summary = scheduler.runOnce();

        assertThat(summary.errors()).isEqualTo(1);
        assertThat(summary.newTimeAndMaterialPeriods()).isEqualTo(1);
        verify(orchestrator).processRun(runId);
    }

    @Test
    void failedOutcome_isReportedNotRetriedWithinTheTick() {
        UUID occurrenceId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        when(runService.findOrCreateForOccurrence(occurrenceId)).thenReturn(run(runId, AutomaticBillingRunStatus.SCHEDULED));
        AutomaticBillingRun failed = run(runId, AutomaticBillingRunStatus.FAILED);
        failed.setFailureStep(AutomaticBillingStep.TAX_CALCULATION);
        failed.setFailureReason("No active tax configuration");
        when(orchestrator.processRun(runId)).thenReturn(failed);
        when(scheduleRepository.findDueOccurrenceIdsWithoutAutomaticRun(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(occurrenceId));

        scheduler.runOnce();

        verify(orchestrator, times(1)).processRun(runId);
    }

    // ================================================================ recovery and retry (Phase 11)

    private AutomaticBillingRun interrupted(UUID runId, int attemptCount) {
        AutomaticBillingRun run = run(runId, AutomaticBillingRunStatus.PROCESSING);
        run.setAttemptCount(attemptCount);
        run.setCurrentStep(AutomaticBillingStep.TAX_CALCULATION);
        when(runService.findById(runId)).thenReturn(java.util.Optional.of(run));
        return run;
    }

    @Test
    void interruptedRunWithAttemptsLeft_isResumed_andOneWithoutIsClosedAsFailed() {
        UUID resumable = UUID.randomUUID();
        UUID exhausted = UUID.randomUUID();
        interrupted(resumable, 1);
        interrupted(exhausted, 3);
        when(runRepository.findInterruptedRunIds(eq(LocalDateTime.now(CLOCK)), any()))
                .thenReturn(List.of(resumable, exhausted));
        when(orchestrator.processRun(resumable)).thenReturn(run(resumable, AutomaticBillingRunStatus.PENDING_APPROVAL));
        when(runService.failInterruptedRun(eq(exhausted), any()))
                .thenReturn(java.util.Optional.of(run(exhausted, AutomaticBillingRunStatus.FAILED)));

        AutomaticBillingScheduler.TickSummary summary = scheduler.runOnce();

        verify(orchestrator).processRun(resumable);
        verify(orchestrator, never()).processRun(exhausted);
        verify(runService).failInterruptedRun(eq(exhausted), org.mockito.ArgumentMatchers.contains(
                "attempt 3 of 3"));
        assertThat(summary.recovered()).isEqualTo(1);
        assertThat(summary.abandoned()).isEqualTo(1);
    }

    @Test
    void dueRetries_areProcessed_andOneFailingRetryDoesNotStopTheOthers() {
        UUID broken = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        when(runRepository.findRunIdsDueForRetry(any(), any())).thenReturn(List.of(broken, healthy));
        when(orchestrator.processRun(broken)).thenThrow(new RuntimeException("database connection reset"));
        when(orchestrator.processRun(healthy)).thenReturn(run(healthy, AutomaticBillingRunStatus.PENDING_APPROVAL));

        AutomaticBillingScheduler.TickSummary summary = scheduler.runOnce();

        verify(orchestrator).processRun(healthy);
        assertThat(summary.retried()).isEqualTo(2);
        assertThat(summary.errors()).isEqualTo(1);
        assertThat(summary.outcome(AutomaticBillingRunStatus.PENDING_APPROVAL)).isEqualTo(1);
    }

    @Test
    void recoveryAndRetries_shareTheBatchWithNewWork() {
        properties.setBatchSize(3);
        UUID interruptedRun = UUID.randomUUID();
        interrupted(interruptedRun, 1);
        when(runRepository.findInterruptedRunIds(any(), any())).thenReturn(List.of(interruptedRun));
        UUID retry = UUID.randomUUID();
        when(runRepository.findRunIdsDueForRetry(any(), any())).thenReturn(List.of(retry));
        when(orchestrator.processRun(any())).thenAnswer(i -> run(i.getArgument(0),
                AutomaticBillingRunStatus.PENDING_APPROVAL));

        scheduler.runOnce();

        ArgumentCaptor<Pageable> retryPage = ArgumentCaptor.forClass(Pageable.class);
        verify(runRepository).findRunIdsDueForRetry(any(), retryPage.capture());
        assertThat(retryPage.getValue().getPageSize()).isEqualTo(2);
        ArgumentCaptor<Pageable> occurrencePage = ArgumentCaptor.forClass(Pageable.class);
        verify(scheduleRepository).findDueOccurrenceIdsWithoutAutomaticRun(any(), any(), any(), any(), any(),
                occurrencePage.capture());
        assertThat(occurrencePage.getValue().getPageSize()).isEqualTo(1);
    }

    // G-30: retry disabled means nothing is attempted again automatically - but
    // an interrupted run is closed as FAILED (visible, needs intervention)
    // instead of staying PROCESSING forever, and explicit re-queues still run.
    @Test
    void retriesDisabled_noAutomaticRetry_interruptedRunClosedNotResumed_requeuedRunProcessed() {
        properties.getRetry().setEnabled(false);
        UUID interruptedRun = UUID.randomUUID();
        UUID requeuedRun = UUID.randomUUID();
        interrupted(interruptedRun, 1);
        when(runRepository.findInterruptedRunIds(eq(LocalDateTime.now(CLOCK)), any()))
                .thenReturn(List.of(interruptedRun));
        when(runService.failInterruptedRun(eq(interruptedRun), any()))
                .thenReturn(java.util.Optional.of(run(interruptedRun, AutomaticBillingRunStatus.FAILED)));
        when(runRepository.findRequeuedRunIdsDue(any(), any())).thenReturn(List.of(requeuedRun));
        when(orchestrator.processRun(requeuedRun))
                .thenReturn(run(requeuedRun, AutomaticBillingRunStatus.PENDING_APPROVAL));

        AutomaticBillingScheduler.TickSummary summary = scheduler.runOnce();

        verify(runRepository, never()).findRunIdsDueForRetry(any(), any());
        verify(orchestrator, never()).processRun(interruptedRun);
        verify(runService).failInterruptedRun(eq(interruptedRun),
                org.mockito.ArgumentMatchers.contains("automatic retry is disabled"));
        verify(orchestrator).processRun(requeuedRun);
        assertThat(summary.abandoned()).isEqualTo(1);
        assertThat(summary.recovered()).isZero();
    }

    // ================================================================ backfill (Phase 11)

    @Test
    void backfillDisabled_onlyPicksUpBillingEventsDueToday() {
        properties.getBackfill().setEnabled(false);
        UUID configurationId = timeAndMaterialConfiguration(List.of(
                period(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
                period(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 11, 1))));

        scheduler.runOnce();

        verify(scheduleRepository).findDueOccurrenceIdsWithoutAutomaticRun(eq(TODAY), eq(TODAY), any(), any(), any(),
                any());
        verify(runService, never()).findOrCreateForTimeAndMaterialPeriod(configurationId,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        verify(runService).findOrCreateForTimeAndMaterialPeriod(configurationId,
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 11, 1));
    }

    @Test
    void backfillWindow_limitsHowFarBackMissedBillingEventsArePickedUp() {
        properties.getBackfill().setMaxAgeDays(45);
        UUID configurationId = timeAndMaterialConfiguration(List.of(
                period(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)),
                period(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
                period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))));

        scheduler.runOnce();

        verify(scheduleRepository).findDueOccurrenceIdsWithoutAutomaticRun(eq(TODAY),
                eq(LocalDate.of(2026, 9, 17)), any(), any(), any(), any());
        // August (billed 31-Aug) is older than the window; September and October are recovered, oldest first.
        verify(runService, never()).findOrCreateForTimeAndMaterialPeriod(configurationId,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(runService);
        order.verify(runService).findOrCreateForTimeAndMaterialPeriod(configurationId,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        order.verify(runService).findOrCreateForTimeAndMaterialPeriod(configurationId,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    }

    @Test
    void timeAndMaterialBackfill_usesEachMissedPeriodItself_neverOneDerivedFromToday() {
        UUID configurationId = timeAndMaterialConfiguration(List.of(
                period(LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 14)),
                period(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 14))));

        scheduler.runOnce();

        verify(runService).findOrCreateForTimeAndMaterialPeriod(configurationId,
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 14));
        verify(runService).findOrCreateForTimeAndMaterialPeriod(configurationId,
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 14));
        verify(occurrenceService).resolveTimeAndMaterialBillingPeriods(configurationId, TODAY);
    }
}
