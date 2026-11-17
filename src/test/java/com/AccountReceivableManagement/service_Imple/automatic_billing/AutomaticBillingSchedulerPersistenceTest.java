package com.AccountReceivableManagement.service_Imple.automatic_billing;

import com.AccountReceivableManagement.config.properties.AutomaticBillingSchedulerProperties;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.entity.automatic_billing.AutomaticBillingRun;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingScheduleType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.integration.billing_data_acquisition.BillingConfigurationIntegration;
import com.AccountReceivableManagement.repo.automatic_billing.AutomaticBillingRunRepository;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.client.ClientRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTypeMasterRepository;
import com.AccountReceivableManagement.service_Imple.projectbilling_config.BillingOccurrenceServiceImpl;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingSnapshotService;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingOccurrenceTransactionService;
import com.AccountReceivableManagement.service_interface.tax_calculation.TaxCalculationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AutomaticBillingScheduler against a real (H2) database: the eligibility
 * queries, and whole ticks through the real run service and orchestrator
 * (billing services mocked) - run creation, no reprocessing, failure
 * isolation, and two schedulers running at once.
 */
@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AutomaticBillingSchedulerPersistenceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T06:00:00Z"), ZoneId.of("UTC"));
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    @Autowired private AutomaticBillingRunRepository runRepository;
    @Autowired private BillingScheduleRepository scheduleRepository;
    @Autowired private BillingConfigurationRepository configurationRepository;
    @Autowired private BillingTypeMasterRepository billingTypeRepository;
    @Autowired private BillingSnapshotRepository snapshotRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private final BillingConfigurationIntegration configurationIntegration = mock(BillingConfigurationIntegration.class);
    private final TaxCalculationService taxService = mock(TaxCalculationService.class);
    private final InvoiceService invoiceService = mock(InvoiceService.class);
    private final BillingSnapshotService snapshotService = mock(BillingSnapshotService.class);

    private TransactionTemplate tx;
    private Client client;
    private AutomaticBillingSchedulerProperties properties;

    @BeforeEach
    void setUp() {
        reset(configurationIntegration, taxService, invoiceService, snapshotService);
        tx = new TransactionTemplate(transactionManager);
        client = tx.execute(status -> clientRepository.save(Client.builder()
                .clientId(UUID.randomUUID()).clientName("Acme").build()));
        properties = new AutomaticBillingSchedulerProperties();
        properties.setEnabled(true);
        properties.setBatchSize(50);

        when(invoiceService.findOrGenerateInvoiceForSchedule(any())).thenAnswer(invocation -> InvoiceResponseDto.builder()
                .invoiceId(UUID.nameUUIDFromBytes(invocation.getArgument(0).toString().getBytes()))
                .status(InvoiceStatus.GENERATED).build());
        when(invoiceService.getInvoiceById(any())).thenAnswer(invocation -> InvoiceResponseDto.builder()
                .invoiceId(invocation.getArgument(0)).status(InvoiceStatus.GENERATED).build());
        when(invoiceService.submitForApproval(any())).thenAnswer(invocation -> InvoiceResponseDto.builder()
                .invoiceId(invocation.getArgument(0)).status(InvoiceStatus.PENDING_APPROVAL).build());
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(status -> {
            runRepository.deleteAll();
            snapshotRepository.deleteAll();
            scheduleRepository.deleteAll();
            configurationRepository.deleteAll();
            billingTypeRepository.deleteAll();
            clientRepository.deleteAll();
        });
    }

    // ---------------------------------------------------------------- fixtures

    private BillingConfiguration configuration(String billingTypeName, ApprovalStatus approval,
            InvoiceGenerationType generation, boolean manuallyDeactivated) {
        BillingConfiguration saved = tx.execute(status -> {
            BillingTypeMaster type = billingTypeRepository.save(BillingTypeMaster.builder()
                    .billingTypeName(billingTypeName).isActive(true).build());
            return configurationRepository.save(BillingConfiguration.builder()
                    .client(client)
                    .billingType(type)
                    .approvalStatus(approval)
                    .invoiceGenerationType(generation)
                    .manuallyDeactivated(manuallyDeactivated)
                    .build());
        });
        when(configurationIntegration.getApprovedBillingConfigurationById(saved.getBillingConfigurationId()))
                .thenReturn(BillingConfigurationResponseDto.builder()
                        .billingConfigurationId(saved.getBillingConfigurationId())
                        .billingType(BillingType.FIXED_PRICE)
                        .billingTypeName(billingTypeName)
                        .approved(approval == ApprovalStatus.APPROVED)
                        .invoiceGenerationType(generation)
                        .build());
        return saved;
    }

    private BillingConfiguration automaticFixedPrice() {
        return configuration("Fixed Price " + UUID.randomUUID(), ApprovalStatus.APPROVED,
                InvoiceGenerationType.AUTOMATIC, false);
    }

    private BillingSchedule occurrence(BillingConfiguration configuration, LocalDate start, LocalDate end,
            BillingPeriodStatus status, boolean active, boolean invoiced) {
        return tx.execute(s -> scheduleRepository.save(BillingSchedule.builder()
                .billingConfiguration(configuration)
                .periodNumber(1)
                .periodStartDate(start)
                .periodEndDate(end)
                .billingDate(end)
                .billingAmount(new BigDecimal("100000.00"))
                .scheduleType(BillingScheduleType.PRIMARY)
                .periodStatus(status)
                .taxStatus(status == BillingPeriodStatus.SCHEDULED ? BillingPeriodStatus.PENDING : status)
                .isInvoiced(invoiced)
                .isActive(active)
                .build()));
    }

    private BillingSchedule dueOctober(BillingConfiguration configuration) {
        return occurrence(configuration, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.TAX_PENDING, true, false);
    }

    private List<UUID> eligible(int limit) {
        return scheduleRepository.findDueOccurrenceIdsWithoutAutomaticRun(TODAY,
                AutomaticBillingScheduler.UNBOUNDED_EARLIEST_BILLING_DATE, ApprovalStatus.APPROVED,
                InvoiceGenerationType.AUTOMATIC, BillingSchedule.UNBILLED_STATUSES, PageRequest.of(0, limit));
    }

    private AutomaticBillingRunServiceImpl runService() {
        return new AutomaticBillingRunServiceImpl(runRepository, scheduleRepository, configurationRepository,
                mock(BillingOccurrenceServiceImpl.class), transactionManager, CLOCK);
    }

    private AutomaticBillingScheduler scheduler() {
        AutomaticBillingRunServiceImpl runService = runService();
        AutomaticBillingOrchestrator orchestrator = new AutomaticBillingOrchestrator(runService,
                configurationIntegration, scheduleRepository, mock(BillingOccurrenceTransactionService.class),
                mock(BillingOccurrenceServiceImpl.class), snapshotService, taxService, invoiceService,
                new AutomaticBillingRetryPolicy(properties, CLOCK), Duration.ofMinutes(15));
        return new AutomaticBillingScheduler(properties, orchestrator, runService, runRepository,
                scheduleRepository, configurationRepository, snapshotRepository,
                mock(BillingOccurrenceServiceImpl.class), transactionManager, CLOCK);
    }

    // ================================================================ eligibility query

    @Test
    void approvedAutomaticDueOccurrence_isEligible() {
        BillingSchedule october = dueOctober(automaticFixedPrice());

        assertThat(eligible(10)).containsExactly(october.getBillingScheduleId());
    }

    @Test
    void manualUnapprovedAndDeactivatedConfigurations_areSkipped() {
        dueOctober(configuration("Manual " + UUID.randomUUID(), ApprovalStatus.APPROVED, InvoiceGenerationType.MANUAL, false));
        dueOctober(configuration("Pending " + UUID.randomUUID(), ApprovalStatus.PENDING_APPROVAL, InvoiceGenerationType.AUTOMATIC, false));
        dueOctober(configuration("Rejected " + UUID.randomUUID(), ApprovalStatus.REJECTED, InvoiceGenerationType.AUTOMATIC, false));
        dueOctober(configuration("Deactivated " + UUID.randomUUID(), ApprovalStatus.APPROVED, InvoiceGenerationType.AUTOMATIC, true));

        assertThat(eligible(10)).isEmpty();
    }

    @Test
    void futureInvoicedInactiveAndCancelledOccurrences_areSkipped() {
        BillingConfiguration configuration = automaticFixedPrice();
        occurrence(configuration, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), BillingPeriodStatus.SCHEDULED, true, false);
        occurrence(configuration, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), BillingPeriodStatus.INVOICED, true, true);
        occurrence(configuration, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), BillingPeriodStatus.TAX_PENDING, false, false);
        occurrence(configuration, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), BillingPeriodStatus.CANCELLED, true, false);

        assertThat(eligible(10)).isEmpty();
    }

    @Test
    void dueOccurrences_inAnyUnbilledStatus_overdueFirst_boundedByTheLimit() {
        BillingConfiguration configuration = automaticFixedPrice();
        BillingSchedule november1 = occurrence(configuration, LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 1),
                BillingPeriodStatus.SCHEDULED, true, false);
        BillingSchedule august = occurrence(configuration, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                BillingPeriodStatus.TAX_CALCULATED, true, false);
        BillingSchedule october = occurrence(configuration, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 24),
                BillingPeriodStatus.TAX_PENDING, true, false);

        assertThat(eligible(10)).containsExactly(august.getBillingScheduleId(), october.getBillingScheduleId(),
                november1.getBillingScheduleId());
        assertThat(eligible(2)).containsExactly(august.getBillingScheduleId(), october.getBillingScheduleId());
    }

    @Test
    void occurrenceWithARunOfAnyStatus_isNoLongerEligible() {
        BillingConfiguration configuration = automaticFixedPrice();
        BillingSchedule october = dueOctober(configuration);
        tx.execute(status -> runRepository.save(AutomaticBillingRun.builder()
                .billingConfiguration(configuration)
                .billingScheduleId(october.getBillingScheduleId())
                .billingPeriodStart(october.getPeriodStartDate())
                .billingPeriodEnd(october.getPeriodEndDate())
                .status(AutomaticBillingRunStatus.FAILED)
                .build()));

        assertThat(eligible(10)).isEmpty();
    }

    @Test
    void timeAndMaterialConfigurationQuery_matchesTheTypeNamesCaseInsensitively() {
        BillingConfiguration timesheet = configuration(" Timesheet Based ", ApprovalStatus.APPROVED,
                InvoiceGenerationType.AUTOMATIC, false);
        configuration("Time & Material", ApprovalStatus.APPROVED, InvoiceGenerationType.MANUAL, false);
        automaticFixedPrice();

        assertThat(configurationRepository.findAutomaticConfigurationIdsByBillingTypeNames(
                ApprovalStatus.APPROVED, InvoiceGenerationType.AUTOMATIC,
                BillingOccurrenceServiceImpl.TIME_AND_MATERIAL_BILLING_TYPE_NAMES))
                .containsExactly(timesheet.getBillingConfigurationId());
    }

    // G-29: configurations that cannot have a period due in the window are not scanned.
    @Test
    void timeAndMaterialWindowQuery_skipsConfigurationsEndedBeforeOrStartingAfterTheWindow() {
        BillingConfiguration open = configuration("Time & Material", ApprovalStatus.APPROVED,
                InvoiceGenerationType.AUTOMATIC, false);
        BillingConfiguration endedInWindow = configuration("Time and Material", ApprovalStatus.APPROVED,
                InvoiceGenerationType.AUTOMATIC, false);
        BillingConfiguration endedBefore = configuration("Timesheet Based", ApprovalStatus.APPROVED,
                InvoiceGenerationType.AUTOMATIC, false);
        BillingConfiguration startsLater = configuration("TIME & MATERIAL", ApprovalStatus.APPROVED,
                InvoiceGenerationType.AUTOMATIC, false);
        tx.executeWithoutResult(status -> {
            setDates(open, LocalDate.of(2026, 1, 1), null);
            setDates(endedInWindow, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 15));
            setDates(endedBefore, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 5, 31));
            setDates(startsLater, LocalDate.of(2026, 12, 1), null);
        });

        assertThat(configurationRepository.findAutomaticConfigurationIdsByBillingTypeNamesDueBetween(
                ApprovalStatus.APPROVED, InvoiceGenerationType.AUTOMATIC,
                BillingOccurrenceServiceImpl.TIME_AND_MATERIAL_BILLING_TYPE_NAMES,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 1)))
                .containsExactlyInAnyOrder(open.getBillingConfigurationId(), endedInWindow.getBillingConfigurationId());

        // Unlimited backfill keeps every historical configuration discoverable.
        assertThat(configurationRepository.findAutomaticConfigurationIdsByBillingTypeNamesDueBetween(
                ApprovalStatus.APPROVED, InvoiceGenerationType.AUTOMATIC,
                BillingOccurrenceServiceImpl.TIME_AND_MATERIAL_BILLING_TYPE_NAMES,
                AutomaticBillingScheduler.UNBOUNDED_EARLIEST_BILLING_DATE, LocalDate.of(2026, 11, 1)))
                .contains(endedBefore.getBillingConfigurationId())
                .doesNotContain(startsLater.getBillingConfigurationId());
    }

    private void setDates(BillingConfiguration configuration, LocalDate from, LocalDate to) {
        BillingConfiguration managed = configurationRepository.findById(configuration.getBillingConfigurationId())
                .orElseThrow();
        managed.setEffectiveFrom(from);
        managed.setEffectiveTo(to);
        configurationRepository.save(managed);
    }

    @Test
    void overlappingSnapshotQuery_detectsAnyOverlapForTheProject() {
        BillingConfiguration configuration = automaticFixedPrice();
        tx.execute(status -> snapshotRepository.save(BillingSnapshot.builder()
                .snapshotNumber("BS-" + UUID.randomUUID().toString().substring(0, 20))
                .billingConfigurationId(configuration.getBillingConfigurationId())
                .clientId(client.getClientId())
                .projectId(23L)
                .billingTypeId(UUID.randomUUID())
                .currencyId(UUID.randomUUID())
                .currencyCode("INR")
                .billingPeriodStart(LocalDate.of(2026, 10, 1))
                .billingPeriodEnd(LocalDate.of(2026, 10, 31))
                .status(BillingSnapshotStatus.INVOICED)
                .build()));

        // 15-Oct..14-Nov overlaps a manual October snapshot; 01-Nov..30-Nov does not.
        assertThat(snapshotRepository.existsByProjectIdAndBillingPeriodStartLessThanEqualAndBillingPeriodEndGreaterThanEqual(
                23L, LocalDate.of(2026, 11, 14), LocalDate.of(2026, 10, 15))).isTrue();
        assertThat(snapshotRepository.existsByProjectIdAndBillingPeriodStartLessThanEqualAndBillingPeriodEndGreaterThanEqual(
                23L, LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1))).isFalse();
        assertThat(snapshotRepository.existsByProjectIdAndBillingPeriodStartLessThanEqualAndBillingPeriodEndGreaterThanEqual(
                99L, LocalDate.of(2026, 11, 14), LocalDate.of(2026, 10, 15))).isFalse();
    }

    // ================================================================ whole ticks

    @Test
    void tick_createsOneRunPerDueOccurrence_andHandsThemToApproval() {
        BillingConfiguration configuration = automaticFixedPrice();
        BillingSchedule october = dueOctober(configuration);
        BillingSchedule september = occurrence(configuration, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                BillingPeriodStatus.SCHEDULED, true, false);

        AutomaticBillingScheduler.TickSummary summary = scheduler().runOnce();

        assertThat(summary.newOccurrences()).isEqualTo(2);
        assertThat(summary.outcome(AutomaticBillingRunStatus.PENDING_APPROVAL)).isEqualTo(2);
        assertThat(runRepository.findAll())
                .extracting(AutomaticBillingRun::getBillingScheduleId, AutomaticBillingRun::getStatus)
                .containsExactlyInAnyOrder(
                        org.assertj.core.api.Assertions.tuple(october.getBillingScheduleId(), AutomaticBillingRunStatus.PENDING_APPROVAL),
                        org.assertj.core.api.Assertions.tuple(september.getBillingScheduleId(), AutomaticBillingRunStatus.PENDING_APPROVAL));
        // The run carries the occurrence's own period.
        assertThat(runRepository.findByBillingScheduleId(october.getBillingScheduleId()).orElseThrow())
                .satisfies(run -> {
                    assertThat(run.getBillingPeriodStart()).isEqualTo(LocalDate.of(2026, 10, 1));
                    assertThat(run.getBillingPeriodEnd()).isEqualTo(LocalDate.of(2026, 10, 31));
                });
    }

    @Test
    void repeatedTicks_neitherDuplicateRunsNorReprocessPendingApproval() {
        BillingSchedule october = dueOctober(automaticFixedPrice());

        AutomaticBillingScheduler scheduler = scheduler();
        scheduler.runOnce();
        AutomaticBillingScheduler.TickSummary second = scheduler.runOnce();

        assertThat(second.handled()).isZero();
        assertThat(runRepository.count()).isEqualTo(1);
        verify(invoiceService, times(1)).findOrGenerateInvoiceForSchedule(october.getBillingScheduleId());
        verify(invoiceService, times(1)).submitForApproval(any());
    }

    @Test
    void completedRun_isNeverReset() {
        BillingConfiguration configuration = automaticFixedPrice();
        BillingSchedule october = dueOctober(configuration);
        UUID runId = tx.execute(status -> runRepository.save(AutomaticBillingRun.builder()
                .billingConfiguration(configuration)
                .billingScheduleId(october.getBillingScheduleId())
                .billingPeriodStart(october.getPeriodStartDate())
                .billingPeriodEnd(october.getPeriodEndDate())
                .status(AutomaticBillingRunStatus.COMPLETED)
                .attemptCount(1)
                .build())).getAutomaticBillingRunId();

        scheduler().runOnce();

        AutomaticBillingRun run = runRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(AutomaticBillingRunStatus.COMPLETED);
        assertThat(run.getAttemptCount()).isEqualTo(1);
        verify(invoiceService, never()).findOrGenerateInvoiceForSchedule(any());
    }

    @Test
    void failedRun_keepsItsFailure_andIsNotRetriedOnTheNextTick() {
        BillingConfiguration configuration = automaticFixedPrice();
        BillingSchedule broken = dueOctober(configuration);
        BillingSchedule healthy = occurrence(configuration, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                BillingPeriodStatus.TAX_PENDING, true, false);
        doThrow(new GlobalExceptionHandler.ValidationException("No active tax configuration is available."))
                .when(taxService).findOrCalculateTaxForSchedule(broken.getBillingScheduleId());

        AutomaticBillingScheduler scheduler = scheduler();
        AutomaticBillingScheduler.TickSummary first = scheduler.runOnce();
        AutomaticBillingScheduler.TickSummary second = scheduler.runOnce();

        assertThat(first.outcome(AutomaticBillingRunStatus.FAILED)).isEqualTo(1);
        assertThat(first.outcome(AutomaticBillingRunStatus.PENDING_APPROVAL)).isEqualTo(1);
        assertThat(second.handled()).isZero();
        AutomaticBillingRun failed = runRepository.findByBillingScheduleId(broken.getBillingScheduleId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(1);
        assertThat(failed.getFailureReason()).contains("No active tax configuration");
        assertThat(runRepository.findByBillingScheduleId(healthy.getBillingScheduleId()).orElseThrow().getStatus())
                .isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        verify(taxService, times(1)).findOrCalculateTaxForSchedule(broken.getBillingScheduleId());
    }

    @Test
    void manualConfiguration_neverReachesTheOrchestrator() {
        dueOctober(configuration("Manual " + UUID.randomUUID(), ApprovalStatus.APPROVED,
                InvoiceGenerationType.MANUAL, false));

        assertThat(scheduler().runOnce().handled()).isZero();
        assertThat(runRepository.count()).isZero();
        verify(taxService, never()).findOrCalculateTaxForSchedule(any());
    }

    @Test
    void scheduledStragglerRun_isPickedUpAndProcessed() {
        BillingConfiguration configuration = automaticFixedPrice();
        BillingSchedule october = dueOctober(configuration);
        UUID runId = runService().findOrCreateForOccurrence(october.getBillingScheduleId()).getAutomaticBillingRunId();

        AutomaticBillingScheduler.TickSummary summary = scheduler().runOnce();

        assertThat(summary.resumed()).isEqualTo(1);
        assertThat(runRepository.findById(runId).orElseThrow().getStatus())
                .isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
    }

    @Test
    void twoSchedulersAtOnce_createOneRunPerOccurrence_andProcessEachOnce() throws Exception {
        BillingConfiguration configuration = automaticFixedPrice();
        List<BillingSchedule> occurrences = List.of(
                dueOctober(configuration),
                occurrence(configuration, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                        BillingPeriodStatus.TAX_PENDING, true, false),
                occurrence(configuration, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                        BillingPeriodStatus.SCHEDULED, true, false));

        // Two independent "instances", each with its own run service and orchestrator.
        AutomaticBillingScheduler instanceA = scheduler();
        AutomaticBillingScheduler instanceB = scheduler();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<AutomaticBillingScheduler.TickSummary>> ticks = new ArrayList<>();
            for (AutomaticBillingScheduler instance : List.of(instanceA, instanceB)) {
                ticks.add(pool.submit(() -> {
                    start.await();
                    return instance.runOnce();
                }));
            }
            start.countDown();
            for (Future<AutomaticBillingScheduler.TickSummary> tick : ticks) {
                tick.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(runRepository.count()).isEqualTo(3);
        for (BillingSchedule occurrence : occurrences) {
            verify(invoiceService, times(1)).findOrGenerateInvoiceForSchedule(occurrence.getBillingScheduleId());
            AutomaticBillingRun run = runRepository.findByBillingScheduleId(occurrence.getBillingScheduleId()).orElseThrow();
            assertThat(run.getAttemptCount()).isEqualTo(1);
        }
        verify(invoiceService, times(3)).submitForApproval(any());
    }
}
