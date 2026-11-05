package com.AccountReceivableManagement.service_Imple.automatic_billing;

import com.AccountReceivableManagement.config.properties.AutomaticBillingSchedulerProperties;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.entity.automatic_billing.AutomaticBillingRun;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingStep;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingScheduleType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType;
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
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 11 against a real (H2) database: scheduler ticks through the real
 * run service, orchestrator and retry policy, all on one clock the test
 * moves forward (billing services mocked). Covers retry with backoff and
 * the attempt limit, backfill of overdue occurrences of every
 * occurrence-based billing type, manual billing respected, and two
 * schedulers retrying at once.
 */
@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AutomaticBillingRecoveryPersistenceTest {

    /** Processing starts 01-Nov-2026 06:00 - after a month of scheduler downtime. */
    private static final Instant START = Instant.parse("2026-11-01T06:00:00Z");

    @Autowired private AutomaticBillingRunRepository runRepository;
    @Autowired private BillingScheduleRepository scheduleRepository;
    @Autowired private BillingConfigurationRepository configurationRepository;
    @Autowired private BillingTypeMasterRepository billingTypeRepository;
    @Autowired private BillingSnapshotRepository snapshotRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private com.AccountReceivableManagement.repo.automatic_billing.AutomaticBillingRunEventRepository eventRepository;
    @Autowired private com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository invoiceRepository;

    private final com.AccountReceivableManagement.security.FinancialActionAuthorizer authorizer =
            mock(com.AccountReceivableManagement.security.FinancialActionAuthorizer.class);
    private static final com.AccountReceivableManagement.security.Actor OPERATOR =
            new com.AccountReceivableManagement.security.Actor(7L, "ops@example.com");

    private final BillingConfigurationIntegration configurationIntegration = mock(BillingConfigurationIntegration.class);
    private final TaxCalculationService taxService = mock(TaxCalculationService.class);
    private final InvoiceService invoiceService = mock(InvoiceService.class);
    private final BillingSnapshotService snapshotService = mock(BillingSnapshotService.class);

    private final MovableClock clock = new MovableClock();
    private TransactionTemplate tx;
    private Client client;
    private AutomaticBillingSchedulerProperties properties;

    @BeforeEach
    void setUp() {
        reset(configurationIntegration, taxService, invoiceService, snapshotService, authorizer);
        when(authorizer.requirePermission(com.AccountReceivableManagement.security.FinancialAction.OPERATIONS_RECOVERY))
                .thenReturn(OPERATOR);
        clock.set(START);
        tx = new TransactionTemplate(transactionManager);
        client = tx.execute(status -> clientRepository.save(Client.builder()
                .clientId(UUID.randomUUID()).clientName("Acme").build()));
        properties = new AutomaticBillingSchedulerProperties();
        properties.setEnabled(true);
        properties.setBatchSize(50);

        when(invoiceService.findOrGenerateInvoiceForSchedule(any())).thenAnswer(invocation -> invoice(
                invoiceIdOf(invocation.getArgument(0)), InvoiceStatus.GENERATED));
        when(invoiceService.getInvoiceById(any())).thenAnswer(invocation -> invoice(invocation.getArgument(0),
                InvoiceStatus.GENERATED));
        when(invoiceService.submitForApproval(any())).thenAnswer(invocation -> invoice(invocation.getArgument(0),
                InvoiceStatus.PENDING_APPROVAL));
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(status -> {
            runRepository.deleteAll();
            eventRepository.deleteAll();
            invoiceRepository.deleteAll();
            snapshotRepository.deleteAll();
            scheduleRepository.deleteAll();
            configurationRepository.deleteAll();
            billingTypeRepository.deleteAll();
            clientRepository.deleteAll();
        });
    }

    // ---------------------------------------------------------------- fixtures

    /** A clock the test advances - shared by the scheduler, run service and retry policy. */
    private static final class MovableClock extends Clock {
        private volatile Instant now;

        void set(Instant instant) {
            now = instant;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static UUID invoiceIdOf(UUID occurrenceId) {
        return UUID.nameUUIDFromBytes(occurrenceId.toString().getBytes());
    }

    private static InvoiceResponseDto invoice(UUID invoiceId, InvoiceStatus status) {
        return InvoiceResponseDto.builder().invoiceId(invoiceId).status(status).build();
    }

    private BillingConfiguration configuration(BillingType billingType, LocalDate effectiveTo) {
        BillingConfiguration saved = tx.execute(status -> {
            BillingTypeMaster type = billingTypeRepository.save(BillingTypeMaster.builder()
                    .billingTypeName(billingType + " " + UUID.randomUUID()).isActive(true).build());
            return configurationRepository.save(BillingConfiguration.builder()
                    .client(client)
                    .billingType(type)
                    .approvalStatus(ApprovalStatus.APPROVED)
                    .invoiceGenerationType(InvoiceGenerationType.AUTOMATIC)
                    .manuallyDeactivated(false)
                    .effectiveTo(effectiveTo)
                    .build());
        });
        when(configurationIntegration.getApprovedBillingConfigurationById(saved.getBillingConfigurationId()))
                .thenReturn(BillingConfigurationResponseDto.builder()
                        .billingConfigurationId(saved.getBillingConfigurationId())
                        .billingType(billingType)
                        .billingTypeName(billingType.name())
                        .approved(true)
                        .invoiceGenerationType(InvoiceGenerationType.AUTOMATIC)
                        .build());
        return saved;
    }

    private BillingSchedule occurrence(BillingConfiguration configuration, int periodNumber, LocalDate start,
            LocalDate end, String amount, BillingPeriodStatus status, boolean invoiced) {
        return tx.execute(s -> scheduleRepository.save(BillingSchedule.builder()
                .billingConfiguration(configuration)
                .periodNumber(periodNumber)
                .periodStartDate(start)
                .periodEndDate(end)
                .billingDate(end)
                .billingAmount(new BigDecimal(amount))
                .scheduleType(BillingScheduleType.PRIMARY)
                .periodStatus(status)
                .taxStatus(status == BillingPeriodStatus.SCHEDULED ? BillingPeriodStatus.PENDING : status)
                .isInvoiced(invoiced)
                .isActive(true)
                .build()));
    }

    private BillingSchedule due(BillingConfiguration configuration, int periodNumber, LocalDate start, LocalDate end,
            String amount) {
        return occurrence(configuration, periodNumber, start, end, amount, BillingPeriodStatus.TAX_PENDING, false);
    }

    private AutomaticBillingScheduler scheduler() {
        AutomaticBillingRunServiceImpl runService = new AutomaticBillingRunServiceImpl(runRepository,
                scheduleRepository, configurationRepository, mock(BillingOccurrenceServiceImpl.class),
                transactionManager, clock);
        AutomaticBillingOrchestrator orchestrator = new AutomaticBillingOrchestrator(runService,
                configurationIntegration, scheduleRepository, mock(BillingOccurrenceTransactionService.class),
                mock(BillingOccurrenceServiceImpl.class), snapshotService, taxService, invoiceService,
                new AutomaticBillingRetryPolicy(properties, clock), Duration.ofMinutes(15));
        AutomaticBillingScheduler scheduler = new AutomaticBillingScheduler(properties, orchestrator, runService,
                runRepository, scheduleRepository, configurationRepository, snapshotRepository,
                mock(BillingOccurrenceServiceImpl.class), transactionManager, clock);
        scheduler.setRecoveryService(recovery());
        return scheduler;
    }

    private AutomaticBillingRunRecoveryServiceImpl recovery() {
        AutomaticBillingRunServiceImpl runService = new AutomaticBillingRunServiceImpl(runRepository,
                scheduleRepository, configurationRepository, mock(BillingOccurrenceServiceImpl.class),
                transactionManager, clock);
        return new AutomaticBillingRunRecoveryServiceImpl(runRepository, eventRepository, runService,
                invoiceRepository, authorizer, transactionManager, clock);
    }

    private AutomaticBillingRun runOf(BillingSchedule occurrence) {
        return runRepository.findByBillingScheduleId(occurrence.getBillingScheduleId()).orElseThrow();
    }

    // ================================================================ retry

    @Test
    void temporaryFailure_isRetriedOnlyOnceItsBackoffHasPassed_andResumesWhereItStopped() {
        BillingSchedule october = due(configuration(BillingType.FIXED_PRICE, null), 1,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "100000.00");
        when(invoiceService.findOrGenerateInvoiceForSchedule(october.getBillingScheduleId()))
                .thenThrow(new QueryTimeoutException("Statement cancelled due to timeout"))
                .thenAnswer(i -> invoice(invoiceIdOf(october.getBillingScheduleId()), InvoiceStatus.GENERATED));
        AutomaticBillingScheduler scheduler = scheduler();

        scheduler.runOnce();
        AutomaticBillingRun failed = runOf(october);
        assertThat(failed.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(failed.getFailureStep()).isEqualTo(AutomaticBillingStep.INVOICE_CREATION);
        assertThat(failed.getNextRetryAt()).isEqualTo(LocalDateTime.ofInstant(START, ZoneOffset.UTC).plusMinutes(15));

        clock.advance(Duration.ofMinutes(5));
        AutomaticBillingScheduler.TickSummary tooEarly = scheduler.runOnce();
        assertThat(tooEarly.retried()).isZero();
        assertThat(runOf(october).getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);

        clock.advance(Duration.ofMinutes(11));
        AutomaticBillingScheduler.TickSummary due = scheduler.runOnce();
        assertThat(due.retried()).isEqualTo(1);

        AutomaticBillingRun recovered = runOf(october);
        assertThat(recovered.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        assertThat(recovered.getAttemptCount()).isEqualTo(2);
        assertThat(recovered.getNextRetryAt()).isNull();
        // Tax was completed in the first attempt and not repeated.
        verify(taxService, times(1)).findOrCalculateTaxForSchedule(october.getBillingScheduleId());
        verify(invoiceService, times(2)).findOrGenerateInvoiceForSchedule(october.getBillingScheduleId());
        verify(invoiceService, times(1)).submitForApproval(any());
        verify(invoiceService, never()).approveInvoice(any());
        verify(invoiceService, never()).finalizeInvoiceDocument(any());
    }

    @Test
    void persistentTemporaryFailure_stopsAfterTheMaximumAttempts() {
        BillingSchedule october = due(configuration(BillingType.FIXED_PRICE, null), 1,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "100000.00");
        when(taxService.findOrCalculateTaxForSchedule(october.getBillingScheduleId()))
                .thenThrow(new QueryTimeoutException("timeout"));
        AutomaticBillingScheduler scheduler = scheduler();

        for (int tick = 0; tick < 8; tick++) {
            scheduler.runOnce();
            clock.advance(Duration.ofHours(2));
        }

        AutomaticBillingRun exhausted = runOf(october);
        assertThat(exhausted.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(exhausted.getAttemptCount()).isEqualTo(3);
        assertThat(exhausted.getNextRetryAt()).isNull();
        verify(taxService, times(3)).findOrCalculateTaxForSchedule(october.getBillingScheduleId());
        verify(invoiceService, never()).findOrGenerateInvoiceForSchedule(any());
    }

    @Test
    void validationFailure_isNeverRetriedAutomatically() {
        BillingSchedule october = due(configuration(BillingType.FIXED_PRICE, null), 1,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "100000.00");
        when(taxService.findOrCalculateTaxForSchedule(october.getBillingScheduleId()))
                .thenThrow(new com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler
                        .ValidationException("No active tax configuration is available."));
        AutomaticBillingScheduler scheduler = scheduler();

        scheduler.runOnce();
        clock.advance(Duration.ofDays(2));
        scheduler.runOnce();

        assertThat(runOf(october).getNextRetryAt()).isNull();
        assertThat(runOf(october).getAttemptCount()).isEqualTo(1);
        verify(taxService, times(1)).findOrCalculateTaxForSchedule(october.getBillingScheduleId());
    }

    @Test
    void interruptedRun_isRecoveredOnceItsClaimHasExpired_andNotBefore() {
        BillingSchedule october = due(configuration(BillingType.FIXED_PRICE, null), 1,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "100000.00");
        AutomaticBillingScheduler scheduler = scheduler();
        scheduler.runOnce();
        // The worker had created the run, done tax and was creating the invoice when the instance stopped.
        tx.executeWithoutResult(status -> {
            AutomaticBillingRun run = runOf(october);
            run.setStatus(AutomaticBillingRunStatus.PROCESSING);
            run.setClaimToken(UUID.randomUUID());
            run.setClaimExpiresAt(LocalDateTime.ofInstant(START, ZoneOffset.UTC).plusMinutes(15));
            run.setLastCompletedStep(AutomaticBillingStep.TAX_CALCULATION);
            run.setCurrentStep(AutomaticBillingStep.INVOICE_CREATION);
            run.setInvoiceId(null);
            runRepository.save(run);
        });
        reset(taxService);

        clock.advance(Duration.ofMinutes(10));
        assertThat(scheduler.runOnce().recovered()).isZero();
        assertThat(runOf(october).getStatus()).isEqualTo(AutomaticBillingRunStatus.PROCESSING);

        clock.advance(Duration.ofMinutes(10));
        assertThat(scheduler.runOnce().recovered()).isEqualTo(1);
        assertThat(runOf(october).getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        verify(taxService, never()).findOrCalculateTaxForSchedule(any());
    }

    @Test
    void twoSchedulersRetryingTheSameRun_processItOnce() throws Exception {
        BillingSchedule october = due(configuration(BillingType.FIXED_PRICE, null), 1,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "100000.00");
        when(invoiceService.findOrGenerateInvoiceForSchedule(october.getBillingScheduleId()))
                .thenThrow(new QueryTimeoutException("timeout"))
                .thenAnswer(i -> invoice(invoiceIdOf(october.getBillingScheduleId()), InvoiceStatus.GENERATED));
        scheduler().runOnce();
        clock.advance(Duration.ofMinutes(16));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<AutomaticBillingScheduler.TickSummary> tick = () -> {
                start.await();
                return scheduler().runOnce();
            };
            List<Future<AutomaticBillingScheduler.TickSummary>> ticks = List.of(pool.submit(tick), pool.submit(tick));
            start.countDown();
            for (Future<AutomaticBillingScheduler.TickSummary> result : ticks) {
                result.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(runOf(october).getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        assertThat(runOf(october).getAttemptCount()).isEqualTo(2);
        verify(invoiceService, times(2)).findOrGenerateInvoiceForSchedule(october.getBillingScheduleId());
        verify(invoiceService, times(1)).submitForApproval(any());
    }

    // ================================================================ backfill

    @Test
    void overdueOccurrencesOfEveryOccurrenceType_areEachBackfilledOnce_withTheirOwnAmounts() {
        BillingConfiguration fixedPrice = configuration(BillingType.FIXED_PRICE, null);
        List<BillingSchedule> fixedPricePeriods = List.of(
                due(fixedPrice, 1, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "333333.33"),
                due(fixedPrice, 2, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "333333.33"),
                due(fixedPrice, 3, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "333333.34"));
        BillingConfiguration installments = configuration(BillingType.MILESTONE, null);
        List<BillingSchedule> installmentPayments = List.of(
                due(installments, 1, LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 15), "200000.00"),
                due(installments, 2, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15), "300000.00"));
        BillingConfiguration retainer = configuration(BillingType.RETAINER, null);
        List<BillingSchedule> retainerMonths = List.of(
                due(retainer, 1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "98630.14"),
                due(retainer, 2, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "101917.81"));
        // Not yet due: never billed early.
        BillingSchedule november = due(retainer, 3, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                "98630.14");
        AutomaticBillingScheduler scheduler = scheduler();

        AutomaticBillingScheduler.TickSummary first = scheduler.runOnce();
        AutomaticBillingScheduler.TickSummary second = scheduler.runOnce();

        assertThat(first.newOccurrences()).isEqualTo(7);
        assertThat(second.handled()).isZero();
        for (List<BillingSchedule> occurrences : List.of(fixedPricePeriods, installmentPayments, retainerMonths)) {
            for (BillingSchedule occurrence : occurrences) {
                // Each occurrence is taxed and invoiced on its own - and so for its own amount.
                verify(taxService, times(1)).findOrCalculateTaxForSchedule(occurrence.getBillingScheduleId());
                verify(invoiceService, times(1)).findOrGenerateInvoiceForSchedule(occurrence.getBillingScheduleId());
                assertThat(runOf(occurrence).getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
                assertThat(runOf(occurrence).getInvoiceId()).isEqualTo(invoiceIdOf(occurrence.getBillingScheduleId()));
            }
        }
        assertThat(runRepository.findByBillingScheduleId(november.getBillingScheduleId())).isEmpty();
        assertThat(runRepository.count()).isEqualTo(7);
    }

    @Test
    void manuallyBilledOccurrences_areRespected() {
        BillingConfiguration configuration = configuration(BillingType.FIXED_PRICE, null);
        BillingSchedule invoicedManually = occurrence(configuration, 1, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31), "100000.00", BillingPeriodStatus.INVOICED, true);
        BillingSchedule taxedManually = occurrence(configuration, 2, LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30), "100000.00", BillingPeriodStatus.TAX_CALCULATED, false);

        scheduler().runOnce();

        assertThat(runRepository.findByBillingScheduleId(invoicedManually.getBillingScheduleId())).isEmpty();
        verify(taxService, never()).findOrCalculateTaxForSchedule(invoicedManually.getBillingScheduleId());
        // A manually started occurrence continues through the same find-or-create steps.
        verify(taxService).findOrCalculateTaxForSchedule(taxedManually.getBillingScheduleId());
        assertThat(runOf(taxedManually).getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
    }

    @Test
    void expiredConfiguration_finalDueOccurrence_isStillBackfilled() {
        BillingConfiguration ended = configuration(BillingType.FIXED_PRICE, LocalDate.of(2026, 9, 30));
        BillingSchedule finalPeriod = due(ended, 6, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                "50000.00");

        scheduler().runOnce();

        assertThat(runOf(finalPeriod).getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
    }

    @Test
    void backfillWindow_leavesOlderOccurrencesToTheManualFlow() {
        properties.getBackfill().setMaxAgeDays(45);
        BillingConfiguration configuration = configuration(BillingType.FIXED_PRICE, null);
        BillingSchedule august = due(configuration, 1, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                "100000.00");
        BillingSchedule october = due(configuration, 3, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                "100000.00");

        scheduler().runOnce();

        assertThat(runRepository.findByBillingScheduleId(august.getBillingScheduleId())).isEmpty();
        assertThat(runOf(october).getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
    }

    @Test
    void oneFailingOverdueOccurrence_doesNotBlockTheOthers() {
        BillingConfiguration configuration = configuration(BillingType.FIXED_PRICE, null);
        BillingSchedule broken = due(configuration, 1, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                "100000.00");
        BillingSchedule healthy = due(configuration, 2, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                "100000.00");
        when(taxService.findOrCalculateTaxForSchedule(broken.getBillingScheduleId()))
                .thenThrow(new QueryTimeoutException("timeout"));

        scheduler().runOnce();

        assertThat(runOf(broken).getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(runOf(broken).getNextRetryAt()).isNotNull();
        assertThat(runOf(healthy).getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
    }
    // ================================================================ G-17 operator re-queue

    private BillingSchedule failedOnValidation() {
        BillingSchedule october = due(configuration(BillingType.FIXED_PRICE, null), 1,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "100000.00");
        when(taxService.findOrCalculateTaxForSchedule(october.getBillingScheduleId()))
                .thenThrow(new com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler
                        .ValidationException("No active tax configuration is available."));
        scheduler().runOnce();
        assertThat(runOf(october).getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(runOf(october).getNextRetryAt()).isNull();
        return october;
    }

    @Test
    void requeue_byAuthorizedOperator_isProcessedOnce_withItsFailureKeptInHistory() {
        BillingSchedule october = failedOnValidation();
        UUID runId = runOf(october).getAutomaticBillingRunId();
        reset(taxService); // Finance fixed the tax configuration.

        var result = recovery().requeue(runId, "Tax configuration for KA added");

        assertThat(result.isAccepted()).isTrue();
        var events = eventRepository.findByRunIdOrderByOccurredAtAsc(runId);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getEventType().name()).isEqualTo("REQUEUED");
            assertThat(event.getFailureReason()).contains("No active tax configuration");
            assertThat(event.getActorUserId()).isEqualTo(7L);
            assertThat(event.getReason()).isEqualTo("Tax configuration for KA added");
        });

        scheduler().runOnce();
        scheduler().runOnce();

        AutomaticBillingRun run = runOf(october);
        assertThat(run.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        assertThat(run.getAttemptCount()).isEqualTo(2);
        verify(invoiceService, times(1)).findOrGenerateInvoiceForSchedule(october.getBillingScheduleId());
    }

    @Test
    void requeue_whileAutomaticRetryIsDisabled_isStillProcessed() {
        BillingSchedule october = failedOnValidation();
        properties.getRetry().setEnabled(false);
        reset(taxService);

        recovery().requeue(runOf(october).getAutomaticBillingRunId(), "Corrected");
        scheduler().runOnce();

        assertThat(runOf(october).getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
    }

    @Test
    void requeue_byUnauthorizedCaller_changesNothing() {
        BillingSchedule october = failedOnValidation();
        UUID runId = runOf(october).getAutomaticBillingRunId();
        when(authorizer.requirePermission(com.AccountReceivableManagement.security.FinancialAction.OPERATIONS_RECOVERY))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("no"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> recovery().requeue(runId, "try"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        assertThat(runOf(october).getNextRetryAt()).isNull();
        assertThat(runOf(october).getRequeuedAt()).isNull();
        assertThat(eventRepository.count()).isZero();
    }

    @Test
    void requeue_ofCompletedPendingOrScheduledRun_isRejectedWithoutChange() {
        BillingSchedule october = failedOnValidation();
        UUID runId = runOf(october).getAutomaticBillingRunId();
        for (AutomaticBillingRunStatus status : List.of(AutomaticBillingRunStatus.COMPLETED,
                AutomaticBillingRunStatus.PENDING_APPROVAL, AutomaticBillingRunStatus.SCHEDULED)) {
            tx.executeWithoutResult(s -> {
                AutomaticBillingRun run = runOf(october);
                run.setStatus(status);
                runRepository.save(run);
            });

            var result = recovery().requeue(runId, "try");

            assertThat(result.isAccepted()).as(status.name()).isFalse();
            assertThat(result.getState()).isEqualTo(status.name());
            assertThat(runOf(october).getNextRetryAt()).isNull();
        }
        assertThat(eventRepository.count()).isZero();
    }

    @Test
    void concurrentRequeues_acceptExactlyOne() throws Exception {
        BillingSchedule october = failedOnValidation();
        UUID runId = runOf(october).getAutomaticBillingRunId();
        AutomaticBillingRunRecoveryServiceImpl recovery = recovery();

        List<Boolean> accepted = concurrently(8, () -> recovery.requeue(runId, "retry").isAccepted());

        assertThat(accepted).containsOnlyOnce(true);
        assertThat(eventRepository.findByRunIdOrderByOccurredAtAsc(runId)).hasSize(1);
    }

    private <T> List<T> concurrently(int threads, Callable<T> work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return work.call();
                }));
            }
            start.countDown();
            List<T> results = new java.util.ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    // ================================================================ G-30 retry disabled

    @Test
    void retryDisabled_interruptedRun_isClosedAsFailedForIntervention_notLeftProcessing() {
        BillingSchedule october = due(configuration(BillingType.FIXED_PRICE, null), 1,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "100000.00");
        AutomaticBillingScheduler scheduler = scheduler();
        scheduler.runOnce();
        tx.executeWithoutResult(status -> {
            AutomaticBillingRun run = runOf(october);
            run.setStatus(AutomaticBillingRunStatus.PROCESSING);
            run.setClaimToken(UUID.randomUUID());
            run.setClaimExpiresAt(LocalDateTime.ofInstant(START, ZoneOffset.UTC).plusMinutes(15));
            run.setLastCompletedStep(AutomaticBillingStep.TAX_CALCULATION);
            run.setCurrentStep(AutomaticBillingStep.INVOICE_CREATION);
            run.setInvoiceId(null);
            runRepository.save(run);
        });
        properties.getRetry().setEnabled(false);
        reset(invoiceService);

        clock.advance(Duration.ofMinutes(20));
        var summary = scheduler.runOnce();

        AutomaticBillingRun run = runOf(october);
        assertThat(summary.abandoned()).isEqualTo(1);
        assertThat(run.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(run.getNextRetryAt()).isNull();
        assertThat(run.getFailureStep()).isEqualTo(AutomaticBillingStep.INVOICE_CREATION);
        assertThat(run.getFailureReason()).contains("automatic retry is disabled");
        verify(invoiceService, never()).findOrGenerateInvoiceForSchedule(any());
    }

    @Test
    void retryDisabled_automaticallyScheduledRetry_isNotProcessed() {
        BillingSchedule october = due(configuration(BillingType.FIXED_PRICE, null), 1,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "100000.00");
        when(taxService.findOrCalculateTaxForSchedule(october.getBillingScheduleId()))
                .thenThrow(new QueryTimeoutException("lock wait timeout"));
        scheduler().runOnce();
        assertThat(runOf(october).getNextRetryAt()).isNotNull();

        properties.getRetry().setEnabled(false);
        clock.advance(Duration.ofDays(1));
        scheduler().runOnce();

        assertThat(runOf(october).getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(runOf(october).getAttemptCount()).isEqualTo(1);
    }

    // ================================================================ G-18 approval reconciliation

    private UUID pendingApprovalRunWithInvoice(BillingSchedule october, InvoiceStatus invoiceStatus) {
        scheduler().runOnce();
        assertThat(runOf(october).getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        com.AccountReceivableManagement.entity.invoice_generation.Invoice invoice = tx.execute(s ->
                invoiceRepository.save(com.AccountReceivableManagement.entity.invoice_generation.Invoice.builder()
                        .invoiceNumber("INV-" + UUID.randomUUID().toString().substring(0, 8))
                        .billingScheduleId(october.getBillingScheduleId())
                        .taxCalculationId(UUID.randomUUID())
                        .clientId(client.getClientId())
                        .projectId(1L)
                        .billingPeriodStart(october.getPeriodStartDate())
                        .billingPeriodEnd(october.getPeriodEndDate())
                        .invoiceDate(LocalDate.of(2026, 11, 1))
                        .generatedAt(LocalDateTime.of(2026, 11, 1, 6, 0))
                        .subtotal(new BigDecimal("100000.00"))
                        .totalTaxAmount(new BigDecimal("18000.00"))
                        .grandTotal(new BigDecimal("118000.00"))
                        .status(invoiceStatus)
                        .build()));
        tx.executeWithoutResult(s -> {
            AutomaticBillingRun run = runOf(october);
            run.setInvoiceId(invoice.getInvoiceId());
            runRepository.save(run);
        });
        return invoice.getInvoiceId();
    }

    private void setInvoiceStatus(UUID invoiceId, InvoiceStatus status) {
        tx.executeWithoutResult(s -> {
            var invoice = invoiceRepository.findById(invoiceId).orElseThrow();
            invoice.setStatus(status);
            invoiceRepository.save(invoice);
        });
    }

    @Test
    void pendingApprovalRun_staysPendingWhileItsInvoiceAwaitsApproval_andCompletesOnceApproved() {
        BillingSchedule october = due(configuration(BillingType.FIXED_PRICE, null), 1,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "100000.00");
        UUID invoiceId = pendingApprovalRunWithInvoice(october, InvoiceStatus.PENDING_APPROVAL);

        assertThat(scheduler().runOnce().reconciled()).isZero();
        assertThat(runOf(october).getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);

        // Approved (and its document finalized), but the after-commit completion was lost.
        setInvoiceStatus(invoiceId, InvoiceStatus.READY_TO_SEND);
        assertThat(scheduler().runOnce().reconciled()).isEqualTo(1);

        AutomaticBillingRun run = runOf(october);
        assertThat(run.getStatus()).isEqualTo(AutomaticBillingRunStatus.COMPLETED);
        assertThat(run.getCompletedAt()).isNotNull();
        assertThat(eventRepository.findByRunIdOrderByOccurredAtAsc(run.getAutomaticBillingRunId()))
                .singleElement().satisfies(event -> {
                    assertThat(event.getEventType().name()).isEqualTo("APPROVAL_RECONCILED");
                    assertThat(event.getActor()).isEqualTo("SYSTEM");
                    assertThat(event.getActorUserId()).isNull();
                });

        // Repeated reconciliation changes nothing more, and nothing is generated again.
        assertThat(scheduler().runOnce().reconciled()).isZero();
        assertThat(eventRepository.count()).isEqualTo(1);
        verify(invoiceService, times(1)).findOrGenerateInvoiceForSchedule(october.getBillingScheduleId());
    }

    @Test
    void pendingApprovalRun_ofRejectedInvoice_isNotCompleted() {
        BillingSchedule october = due(configuration(BillingType.FIXED_PRICE, null), 1,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "100000.00");
        pendingApprovalRunWithInvoice(october, InvoiceStatus.REJECTED);

        var result = recovery().reconcileApproval(runOf(october).getAutomaticBillingRunId());

        assertThat(result.isAccepted()).isFalse();
        assertThat(result.getMessage()).contains("rejected");
        assertThat(runOf(october).getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        assertThat(scheduler().runOnce().reconciled()).isZero();
    }

    @Test
    void concurrentApprovalReconciliation_completesTheRunOnce() throws Exception {
        BillingSchedule october = due(configuration(BillingType.FIXED_PRICE, null), 1,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "100000.00");
        pendingApprovalRunWithInvoice(october, InvoiceStatus.APPROVED);
        UUID runId = runOf(october).getAutomaticBillingRunId();
        AutomaticBillingRunRecoveryServiceImpl recovery = recovery();

        List<Boolean> accepted = concurrently(6, () -> recovery.reconcileApproval(runId).isAccepted());

        assertThat(accepted).containsOnlyOnce(true);
        assertThat(runOf(october).getStatus()).isEqualTo(AutomaticBillingRunStatus.COMPLETED);
        assertThat(eventRepository.findByRunIdOrderByOccurredAtAsc(runId)).hasSize(1);
    }
}
