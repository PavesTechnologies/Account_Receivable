package com.AccountReceivableManagement.service_Imple.automatic_billing;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotCreateRequestDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotResponseDto;
import com.AccountReceivableManagement.dto.common.ApiResponse;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.entity.automatic_billing.AutomaticBillingRun;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingStep;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import com.AccountReceivableManagement.config.properties.AutomaticBillingSchedulerProperties;
import com.AccountReceivableManagement.integration.billing_data_acquisition.TmsServiceUnavailableException;
import com.AccountReceivableManagement.service_interface.automatic_billing.AutomaticBillingRunService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AutomaticBillingOrchestrator against real run state (H2, real
 * AutomaticBillingRunServiceImpl): routing per billing type, step order,
 * resume, idempotency, failure recording and concurrent processing. The
 * billing services it coordinates are mocked - their own behaviour
 * (acquisition, tax, line items, find-or-create) is covered by their tests.
 */
@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class AutomaticBillingOrchestratorTest {

    /** Processing date 01-Nov-2026: October occurrences and the 15-Oct..14-Nov period's predecessor are due. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneId.of("UTC"));

    /** Defaults: 3 attempts, 15 minutes doubling, at most 6 hours. */
    private final AutomaticBillingRetryPolicy retryPolicy =
            new AutomaticBillingRetryPolicy(new AutomaticBillingSchedulerProperties(), CLOCK);
    private static final Long PROJECT_ID = 23L;

    @Autowired private AutomaticBillingRunRepository runRepository;
    @Autowired private BillingScheduleRepository scheduleRepository;
    @Autowired private BillingConfigurationRepository configurationRepository;
    @Autowired private BillingTypeMasterRepository billingTypeRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private final BillingConfigurationIntegration configurationIntegration = mock(BillingConfigurationIntegration.class);
    private final BillingOccurrenceTransactionService occurrenceTransitions = mock(BillingOccurrenceTransactionService.class);
    private final BillingOccurrenceServiceImpl occurrenceService = mock(BillingOccurrenceServiceImpl.class);
    private final BillingSnapshotService snapshotService = mock(BillingSnapshotService.class);
    private final TaxCalculationService taxService = mock(TaxCalculationService.class);
    private final InvoiceService invoiceService = mock(InvoiceService.class);

    private AutomaticBillingRunServiceImpl runService;
    private AutomaticBillingOrchestrator orchestrator;
    private TransactionTemplate tx;
    private BillingConfiguration configuration;

    private final UUID snapshotId = UUID.randomUUID();
    private final UUID invoiceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(configurationIntegration, occurrenceTransitions, occurrenceService, snapshotService, taxService,
                invoiceService);
        tx = new TransactionTemplate(transactionManager);
        runService = new AutomaticBillingRunServiceImpl(runRepository, scheduleRepository, configurationRepository,
                mock(BillingOccurrenceServiceImpl.class), transactionManager, CLOCK);
        orchestrator = new AutomaticBillingOrchestrator(runService, configurationIntegration, scheduleRepository,
                occurrenceTransitions, occurrenceService, snapshotService, taxService, invoiceService,
                retryPolicy, Duration.ofMinutes(15));

        configuration = tx.execute(status -> {
            Client client = clientRepository.save(Client.builder()
                    .clientId(UUID.randomUUID()).clientName("Acme").build());
            BillingTypeMaster type = billingTypeRepository.save(BillingTypeMaster.builder()
                    .billingTypeName("Type " + UUID.randomUUID()).isActive(true).build());
            return configurationRepository.save(BillingConfiguration.builder()
                    .client(client)
                    .billingType(type)
                    .approvalStatus(ApprovalStatus.APPROVED)
                    .invoiceGenerationType(InvoiceGenerationType.AUTOMATIC)
                    .build());
        });

        stubInvoice(InvoiceStatus.GENERATED);
        when(invoiceService.submitForApproval(invoiceId)).thenReturn(invoice(InvoiceStatus.PENDING_APPROVAL));
        when(snapshotService.createBillingSnapshot(any())).thenReturn(ApiResponse.success("created",
                BillingSnapshotResponseDto.builder().snapshotId(snapshotId).build()));
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(status -> {
            runRepository.deleteAll();
            scheduleRepository.deleteAll();
            configurationRepository.deleteAll();
            billingTypeRepository.deleteAll();
            clientRepository.deleteAll();
        });
    }

    // ---------------------------------------------------------------- fixtures

    private InvoiceResponseDto invoice(InvoiceStatus status) {
        return InvoiceResponseDto.builder().invoiceId(invoiceId).invoiceNumber("INV-1").status(status).build();
    }

    private void stubInvoice(InvoiceStatus status) {
        when(invoiceService.findOrGenerateInvoice(any())).thenReturn(invoice(status));
        when(invoiceService.findOrGenerateInvoiceForSchedule(any())).thenReturn(invoice(status));
        when(invoiceService.getInvoiceById(invoiceId)).thenReturn(invoice(status));
    }

    private void configurationIs(BillingType type, boolean approved, InvoiceGenerationType generation) {
        when(configurationIntegration.getApprovedBillingConfigurationById(configuration.getBillingConfigurationId()))
                .thenReturn(BillingConfigurationResponseDto.builder()
                        .billingConfigurationId(configuration.getBillingConfigurationId())
                        .projectId(PROJECT_ID)
                        .billingType(type)
                        .billingTypeName(type.name())
                        .approved(approved)
                        .invoiceGenerationType(generation)
                        .build());
    }

    private void configurationIs(BillingType type) {
        configurationIs(type, true, InvoiceGenerationType.AUTOMATIC);
    }

    /** A T&amp;M run as AutomaticBillingRunService creates it - no occurrence, the configuration's own period. */
    private UUID timeAndMaterialRun(LocalDate start, LocalDate end) {
        return tx.execute(status -> runRepository.save(AutomaticBillingRun.builder()
                .billingConfiguration(configuration)
                .billingPeriodStart(start)
                .billingPeriodEnd(end)
                .build())).getAutomaticBillingRunId();
    }

    private BillingSchedule occurrence(LocalDate start, LocalDate end, BillingPeriodStatus status) {
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
                .isInvoiced(false)
                .isActive(true)
                .build()));
    }

    private UUID occurrenceRun(BillingSchedule occurrence) {
        return runService.findOrCreateForOccurrence(occurrence.getBillingScheduleId()).getAutomaticBillingRunId();
    }

    /** Puts a run where an earlier, interrupted attempt left it. */
    private void interruptedAfter(UUID runId, AutomaticBillingStep lastCompleted, UUID snapshot, UUID invoice) {
        tx.executeWithoutResult(status -> {
            AutomaticBillingRun run = runRepository.findById(runId).orElseThrow();
            run.setStatus(AutomaticBillingRunStatus.FAILED);
            run.setAttemptCount(1);
            run.setLastCompletedStep(lastCompleted);
            run.setBillingSnapshotId(snapshot);
            run.setInvoiceId(invoice);
            runRepository.save(run);
        });
    }

    private AutomaticBillingRun stored(UUID runId) {
        return runRepository.findById(runId).orElseThrow();
    }

    // ================================================================ basic processing

    @Test
    void timeAndMaterialRun_isProcessedThroughEveryStepToPendingApproval() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        assertThat(result.getLastCompletedStep()).isEqualTo(AutomaticBillingStep.SUBMIT_FOR_APPROVAL);
        assertThat(result.getBillingSnapshotId()).isEqualTo(snapshotId);
        assertThat(result.getInvoiceId()).isEqualTo(invoiceId);
        assertThat(result.getAttemptCount()).isEqualTo(1);
        assertThat(result.getClaimToken()).isNull();
        verify(taxService).findOrCalculateTax(snapshotId);
        verify(invoiceService).findOrGenerateInvoice(snapshotId);
        verify(invoiceService).submitForApproval(invoiceId);
        verify(invoiceService, never()).approveInvoice(any());
    }

    @Test
    void unknownRun_isRejected() {
        assertThatThrownBy(() -> orchestrator.processRun(UUID.randomUUID()))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class);
    }

    @Test
    void configurationSwitchedToManual_isNotProcessed() {
        configurationIs(BillingType.TIME_AND_MATERIAL, true, InvoiceGenerationType.MANUAL);
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(result.getFailureReason()).contains("no longer uses AUTOMATIC");
        assertThat(result.getFailureStep()).isNull();
        verifyNoInteractions(snapshotService, taxService);
    }

    @Test
    void unapprovedConfiguration_isNotProcessed() {
        configurationIs(BillingType.FIXED_PRICE, false, InvoiceGenerationType.AUTOMATIC);
        UUID runId = occurrenceRun(occurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.TAX_PENDING));

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(result.getFailureReason()).contains("is not APPROVED");
        verifyNoInteractions(taxService, occurrenceTransitions);
    }

    @Test
    void billingTypeThatDoesNotMatchTheRun_isNotProcessed() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        UUID runId = occurrenceRun(occurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.TAX_PENDING));

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        verifyNoInteractions(snapshotService, taxService);
    }

    // ================================================================ T&M

    @Test
    void timeAndMaterial_acquiresExactlyTheRunsOwnPeriodForTheConfigurationAndProject() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        // A mid-month cycle - passed through as is, never turned into a calendar month.
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 14));

        orchestrator.processRun(runId);

        ArgumentCaptor<BillingSnapshotCreateRequestDto> request =
                ArgumentCaptor.forClass(BillingSnapshotCreateRequestDto.class);
        verify(snapshotService).createBillingSnapshot(request.capture());
        assertThat(request.getValue().getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(request.getValue().getBillingConfigurationId()).isEqualTo(configuration.getBillingConfigurationId());
        assertThat(request.getValue().getBillingPeriodStart()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(request.getValue().getBillingPeriodEnd()).isEqualTo(LocalDate.of(2026, 10, 14));
        verifyNoInteractions(occurrenceTransitions);
        verify(taxService, never()).findOrCalculateTaxForSchedule(any());
    }

    @Test
    void timeAndMaterial_resumedAfterAcquisition_reusesTheSnapshotWithoutCallingTmsAgain() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        interruptedAfter(runId, AutomaticBillingStep.SOURCE_DATA_ACQUISITION, snapshotId, null);

        AutomaticBillingRun result = orchestrator.processRun(runId);

        verifyNoInteractions(snapshotService);
        verify(taxService).findOrCalculateTax(snapshotId);
        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        assertThat(result.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void timeAndMaterial_tmsFailure_failsAtAcquisitionAndCreatesNothingDownstream() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        when(snapshotService.createBillingSnapshot(any()))
                .thenThrow(new IllegalStateException("TMS service failed with status 503."));
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(result.getFailureStep()).isEqualTo(AutomaticBillingStep.SOURCE_DATA_ACQUISITION);
        assertThat(result.getFailureReason()).isEqualTo("TMS service failed with status 503.");
        assertThat(result.getAttemptCount()).isEqualTo(1);
        assertThat(result.getLastCompletedStep()).isNull();
        verifyNoInteractions(taxService, invoiceService);
    }

    @Test
    void timeAndMaterial_rejectedAcquisition_failsWithTheValidatorsMessage() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        when(snapshotService.createBillingSnapshot(any()))
                .thenReturn(ApiResponse.failure("No timesheets were acquired for the requested billing period."));
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(result.getFailureStep()).isEqualTo(AutomaticBillingStep.SOURCE_DATA_ACQUISITION);
        assertThat(result.getFailureReason()).contains("No timesheets were acquired");
        verifyNoInteractions(taxService);
    }

    @Test
    void timeAndMaterial_weeklyAndCalendarMonthPeriods_areAcquiredExactlyAsScheduled() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        UUID weekly = timeAndMaterialRun(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11));
        // Previous month, processed on 1 November (the test clock) - the processing date is not the period.
        UUID monthly = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        orchestrator.processRun(weekly);
        orchestrator.processRun(monthly);

        ArgumentCaptor<BillingSnapshotCreateRequestDto> request =
                ArgumentCaptor.forClass(BillingSnapshotCreateRequestDto.class);
        verify(snapshotService, times(2)).createBillingSnapshot(request.capture());
        assertThat(request.getAllValues())
                .extracting(BillingSnapshotCreateRequestDto::getBillingPeriodStart,
                        BillingSnapshotCreateRequestDto::getBillingPeriodEnd)
                .containsExactly(
                        tuple(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11)),
                        tuple(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
    }

    @Test
    void timeAndMaterial_missingRate_failsAtAcquisitionWithoutTaxOrInvoice() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        when(snapshotService.createBillingSnapshot(any())).thenReturn(ApiResponse.failure(
                "No active hourly rate found for billing configuration X and work date 2026-10-07, role Architect."));
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(result.getFailureStep()).isEqualTo(AutomaticBillingStep.SOURCE_DATA_ACQUISITION);
        assertThat(result.getFailureReason()).contains("No active hourly rate", "role Architect");
        assertThat(result.getBillingSnapshotId()).isNull();
        verifyNoInteractions(taxService, invoiceService);
    }

    @Test
    void timeAndMaterial_reusedCancelledSnapshot_isNotBilled() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        when(snapshotService.createBillingSnapshot(any())).thenReturn(ApiResponse.success("exists",
                BillingSnapshotResponseDto.builder().snapshotId(snapshotId).snapshotNumber("BS-1")
                        .status(BillingSnapshotStatus.CANCELLED).build()));
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(result.getFailureStep()).isEqualTo(AutomaticBillingStep.SOURCE_DATA_ACQUISITION);
        assertThat(result.getFailureReason()).contains("BS-1", "CANCELLED");
        assertThat(result.getBillingSnapshotId()).isNull();
        verifyNoInteractions(taxService, invoiceService);
    }

    @Test
    void timeAndMaterial_tmsRefusesBackgroundCall_recordsTheAuthenticationDependency() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        when(snapshotService.createBillingSnapshot(any())).thenThrow(new IllegalStateException(
                "TMS refused the request (status 401): no Authorization was available - background TMS access "
                        + "requires service-to-service authentication, which is not configured."));
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(result.getFailureStep()).isEqualTo(AutomaticBillingStep.SOURCE_DATA_ACQUISITION);
        assertThat(result.getFailureReason()).contains("service-to-service authentication");
        verifyNoInteractions(taxService, invoiceService);
    }

    // ================================================================ occurrence-based types

    @Test
    void fixedPrice_usesTheOccurrenceAndNeverCallsTms() {
        configurationIs(BillingType.FIXED_PRICE);
        BillingSchedule occurrence = occurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.SCHEDULED);
        UUID runId = occurrenceRun(occurrence);

        AutomaticBillingRun result = orchestrator.processRun(runId);

        // Due but still SCHEDULED: made TAX_PENDING through the existing transition.
        ArgumentCaptor<BillingSchedule> transitioned = ArgumentCaptor.forClass(BillingSchedule.class);
        verify(occurrenceTransitions).transitionSingleOccurrenceToTaxPending(transitioned.capture());
        assertThat(transitioned.getValue().getBillingScheduleId()).isEqualTo(occurrence.getBillingScheduleId());
        verify(taxService).findOrCalculateTaxForSchedule(occurrence.getBillingScheduleId());
        verify(invoiceService).findOrGenerateInvoiceForSchedule(occurrence.getBillingScheduleId());
        verifyNoInteractions(snapshotService);
        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        assertThat(result.getBillingSnapshotId()).isNull();
        assertThat(result.getInvoiceId()).isEqualTo(invoiceId);
    }

    @Test
    void fixedPrice_occurrenceIsCheckedBeforeAnythingIsTaxed() {
        configurationIs(BillingType.FIXED_PRICE);
        BillingSchedule occurrence = occurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.SCHEDULED);

        orchestrator.processRun(occurrenceRun(occurrence));

        InOrder order = inOrder(occurrenceService, occurrenceTransitions, taxService, invoiceService);
        order.verify(occurrenceService).requireFixedPriceOccurrenceBillable(occurrence.getBillingScheduleId());
        order.verify(occurrenceTransitions).transitionSingleOccurrenceToTaxPending(any());
        order.verify(taxService).findOrCalculateTaxForSchedule(occurrence.getBillingScheduleId());
        order.verify(invoiceService).findOrGenerateInvoiceForSchedule(occurrence.getBillingScheduleId());
        order.verify(invoiceService).submitForApproval(invoiceId);
        verify(invoiceService, never()).approveInvoice(any());
    }

    @Test
    void fixedPrice_unbillableOccurrence_failsAtSourceDataWithoutTaxOrInvoice() {
        configurationIs(BillingType.FIXED_PRICE);
        BillingSchedule occurrence = occurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.SCHEDULED);
        doThrow(new GlobalExceptionHandler.ValidationException(
                "No currency is configured for Billing Configuration X."))
                .when(occurrenceService).requireFixedPriceOccurrenceBillable(occurrence.getBillingScheduleId());

        AutomaticBillingRun result = orchestrator.processRun(occurrenceRun(occurrence));

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(result.getFailureStep()).isEqualTo(AutomaticBillingStep.SOURCE_DATA_ACQUISITION);
        assertThat(result.getFailureReason()).isEqualTo("No currency is configured for Billing Configuration X.");
        assertThat(result.getLastCompletedStep()).isNull();
        verifyNoInteractions(occurrenceTransitions, taxService, invoiceService);
    }

    @Test
    void fixedPrice_approvalSubmissionFailure_keepsTheInvoice_andTheNextAttemptOnlyResubmits() {
        configurationIs(BillingType.FIXED_PRICE);
        BillingSchedule occurrence = occurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.TAX_PENDING);
        when(invoiceService.submitForApproval(invoiceId))
                .thenThrow(new GlobalExceptionHandler.ValidationException("Invoice is missing client email."))
                .thenReturn(invoice(InvoiceStatus.PENDING_APPROVAL));
        UUID runId = occurrenceRun(occurrence);

        AutomaticBillingRun failed = orchestrator.processRun(runId);

        assertThat(failed.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(failed.getFailureStep()).isEqualTo(AutomaticBillingStep.SUBMIT_FOR_APPROVAL);
        assertThat(failed.getLastCompletedStep()).isEqualTo(AutomaticBillingStep.INVOICE_CREATION);
        assertThat(failed.getInvoiceId()).isEqualTo(invoiceId);

        AutomaticBillingRun resumed = orchestrator.processRun(runId);

        assertThat(resumed.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        assertThat(resumed.getInvoiceId()).isEqualTo(invoiceId);
        verify(occurrenceService, times(1)).requireFixedPriceOccurrenceBillable(any());
        verify(taxService, times(1)).findOrCalculateTaxForSchedule(any());
        verify(invoiceService, times(1)).findOrGenerateInvoiceForSchedule(any());
        verify(invoiceService, times(2)).submitForApproval(invoiceId);
        verify(invoiceService, never()).approveInvoice(any());
    }

    @Test
    void eachOccurrenceBasedType_isCheckedByItsOwnSource() {
        configurationIs(BillingType.MILESTONE);
        BillingSchedule milestone = occurrence(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15),
                BillingPeriodStatus.TAX_PENDING);
        orchestrator.processRun(occurrenceRun(milestone));

        verify(occurrenceService).requireMilestonePlanOccurrenceBillable(milestone.getBillingScheduleId());
        verify(occurrenceService, never()).requireFixedPriceOccurrenceBillable(any());

        configurationIs(BillingType.RETAINER);
        BillingSchedule month = occurrence(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                BillingPeriodStatus.TAX_PENDING);
        orchestrator.processRun(occurrenceRun(month));

        verify(occurrenceService).requireRecurringOccurrenceBillable(month.getBillingScheduleId());
        verify(occurrenceService, times(1)).requireMilestonePlanOccurrenceBillable(any());
        verify(occurrenceService, never()).requireFixedPriceOccurrenceBillable(any());
    }

    // ================================================================ recurring / retainer

    @Test
    void recurring_isCheckedBeforeTax_andStopsAtPendingApproval() {
        configurationIs(BillingType.RETAINER);
        BillingSchedule october = occurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.SCHEDULED);

        AutomaticBillingRun result = orchestrator.processRun(occurrenceRun(october));

        InOrder order = inOrder(occurrenceService, occurrenceTransitions, taxService, invoiceService);
        order.verify(occurrenceService).requireRecurringOccurrenceBillable(october.getBillingScheduleId());
        order.verify(occurrenceTransitions).transitionSingleOccurrenceToTaxPending(any());
        order.verify(taxService).findOrCalculateTaxForSchedule(october.getBillingScheduleId());
        order.verify(invoiceService).findOrGenerateInvoiceForSchedule(october.getBillingScheduleId());
        order.verify(invoiceService).submitForApproval(invoiceId);
        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        assertThat(result.getBillingPeriodStart()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(result.getBillingPeriodEnd()).isEqualTo(LocalDate.of(2026, 10, 31));
        verify(invoiceService, never()).approveInvoice(any());
        verifyNoInteractions(snapshotService);
    }

    @Test
    void recurring_overlappingPeriod_failsBeforeAnythingIsTaxed() {
        configurationIs(BillingType.RETAINER);
        BillingSchedule overlapping = occurrence(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 14),
                BillingPeriodStatus.SCHEDULED);
        doThrow(new GlobalExceptionHandler.ValidationException(
                "Billing period 2026-09-15 to 2026-10-14 overlaps billing period 2026-09-01 to 2026-09-30 (INVOICED)."))
                .when(occurrenceService).requireRecurringOccurrenceBillable(overlapping.getBillingScheduleId());

        AutomaticBillingRun result = orchestrator.processRun(occurrenceRun(overlapping));

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(result.getFailureStep()).isEqualTo(AutomaticBillingStep.SOURCE_DATA_ACQUISITION);
        assertThat(result.getFailureReason()).contains("overlaps");
        verifyNoInteractions(occurrenceTransitions, taxService, invoiceService);
    }

    @Test
    void recurring_interruptedAfterInvoiceCreation_onlyResubmits() {
        configurationIs(BillingType.RETAINER);
        BillingSchedule october = occurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.INVOICED);
        // The occurrence is already INVOICED, so the run predates it (as an interrupted run would).
        UUID runId = tx.execute(status -> runRepository.save(AutomaticBillingRun.builder()
                .billingConfiguration(configuration)
                .billingScheduleId(october.getBillingScheduleId())
                .billingPeriodStart(october.getPeriodStartDate())
                .billingPeriodEnd(october.getPeriodEndDate())
                .build())).getAutomaticBillingRunId();
        interruptedAfter(runId, AutomaticBillingStep.INVOICE_CREATION, null, invoiceId);

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        verifyNoInteractions(occurrenceService, occurrenceTransitions);
        verify(taxService, never()).findOrCalculateTaxForSchedule(any());
        verify(invoiceService, never()).findOrGenerateInvoiceForSchedule(any());
        verify(invoiceService).submitForApproval(invoiceId);
    }

    @Test
    void oneFailingRecurringPeriod_doesNotAffectTheNextPeriod() {
        configurationIs(BillingType.RETAINER);
        BillingSchedule september = occurrence(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                BillingPeriodStatus.TAX_PENDING);
        BillingSchedule october = occurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.TAX_PENDING);
        when(invoiceService.findOrGenerateInvoiceForSchedule(september.getBillingScheduleId()))
                .thenThrow(new GlobalExceptionHandler.ResourceNotFoundException("No active company profile."));

        AutomaticBillingRun failed = orchestrator.processRun(occurrenceRun(september));
        AutomaticBillingRun billed = orchestrator.processRun(occurrenceRun(october));

        assertThat(failed.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(failed.getFailureStep()).isEqualTo(AutomaticBillingStep.INVOICE_CREATION);
        assertThat(billed.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
    }

    // ================================================================ milestone / installment

    @Test
    void milestone_isCheckedBeforeTax_andStopsAtPendingApproval() {
        configurationIs(BillingType.MILESTONE);
        BillingSchedule milestone = occurrence(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15),
                BillingPeriodStatus.SCHEDULED);

        AutomaticBillingRun result = orchestrator.processRun(occurrenceRun(milestone));

        InOrder order = inOrder(occurrenceService, occurrenceTransitions, taxService, invoiceService);
        order.verify(occurrenceService).requireMilestonePlanOccurrenceBillable(milestone.getBillingScheduleId());
        order.verify(occurrenceTransitions).transitionSingleOccurrenceToTaxPending(any());
        order.verify(taxService).findOrCalculateTaxForSchedule(milestone.getBillingScheduleId());
        order.verify(invoiceService).findOrGenerateInvoiceForSchedule(milestone.getBillingScheduleId());
        order.verify(invoiceService).submitForApproval(invoiceId);
        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        assertThat(result.getInvoiceId()).isEqualTo(invoiceId);
        verify(invoiceService, never()).approveInvoice(any());
        verifyNoInteractions(snapshotService);
    }

    @Test
    void installment_thatNoLongerMatchesItsPaymentEntry_failsBeforeAnythingIsTaxed() {
        configurationIs(BillingType.MILESTONE);
        BillingSchedule installment = occurrence(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15),
                BillingPeriodStatus.TAX_PENDING);
        doThrow(new GlobalExceptionHandler.ValidationException(
                "Payment 2 is now 300000.00 but its Billing Occurrence is 250000.00; the occurrence must be "
                        + "reconciled before it is billed."))
                .when(occurrenceService).requireMilestonePlanOccurrenceBillable(installment.getBillingScheduleId());

        AutomaticBillingRun result = orchestrator.processRun(occurrenceRun(installment));

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(result.getFailureStep()).isEqualTo(AutomaticBillingStep.SOURCE_DATA_ACQUISITION);
        assertThat(result.getFailureReason()).contains("Payment 2", "must be reconciled");
        assertThat(result.getLastCompletedStep()).isNull();
        verifyNoInteractions(occurrenceTransitions, taxService, invoiceService);
    }

    @Test
    void oneFailingInstallment_doesNotAffectTheNextInstallment() {
        configurationIs(BillingType.MILESTONE);
        BillingSchedule broken = occurrence(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 15),
                BillingPeriodStatus.TAX_PENDING);
        BillingSchedule next = occurrence(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15),
                BillingPeriodStatus.TAX_PENDING);
        when(taxService.findOrCalculateTaxForSchedule(broken.getBillingScheduleId()))
                .thenThrow(new GlobalExceptionHandler.ValidationException("No active tax configuration."));

        AutomaticBillingRun failed = orchestrator.processRun(occurrenceRun(broken));
        AutomaticBillingRun billed = orchestrator.processRun(occurrenceRun(next));

        assertThat(failed.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(failed.getFailureStep()).isEqualTo(AutomaticBillingStep.TAX_CALCULATION);
        assertThat(billed.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        verify(invoiceService, never()).findOrGenerateInvoiceForSchedule(broken.getBillingScheduleId());
        verify(invoiceService).findOrGenerateInvoiceForSchedule(next.getBillingScheduleId());
    }

    @Test
    void milestone_interruptedAfterTax_resumesWithoutRecalculatingTax() {
        configurationIs(BillingType.MILESTONE);
        BillingSchedule milestone = occurrence(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15),
                BillingPeriodStatus.TAX_CALCULATED);
        UUID runId = occurrenceRun(milestone);
        interruptedAfter(runId, AutomaticBillingStep.TAX_CALCULATION, null, null);

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        verifyNoInteractions(occurrenceService, occurrenceTransitions);
        verify(taxService, never()).findOrCalculateTaxForSchedule(any());
        verify(invoiceService, times(1)).findOrGenerateInvoiceForSchedule(milestone.getBillingScheduleId());
    }

    @Test
    void milestone_occurrenceAlreadyTaxPending_isNotTransitionedAgain() {
        configurationIs(BillingType.MILESTONE);
        BillingSchedule milestone = occurrence(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 20),
                BillingPeriodStatus.TAX_PENDING);

        orchestrator.processRun(occurrenceRun(milestone));

        verifyNoInteractions(occurrenceTransitions);
        verify(taxService).findOrCalculateTaxForSchedule(milestone.getBillingScheduleId());
        // The milestone line itself is produced by generateInvoiceForSchedule (Phase 1).
        verify(invoiceService).findOrGenerateInvoiceForSchedule(milestone.getBillingScheduleId());
    }

    @Test
    void installments_eachInstallmentIsItsOwnRunAndInvoice_andReprocessingDuplicatesNothing() {
        configurationIs(BillingType.MILESTONE);
        List<BillingSchedule> installments = List.of(
                occurrence(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 15), BillingPeriodStatus.TAX_PENDING),
                occurrence(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15), BillingPeriodStatus.TAX_PENDING),
                occurrence(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 31), BillingPeriodStatus.TAX_PENDING));

        List<UUID> runIds = installments.stream().map(this::occurrenceRun).toList();
        runIds.forEach(orchestrator::processRun);
        runIds.forEach(orchestrator::processRun);

        assertThat(runIds).doesNotHaveDuplicates();
        for (BillingSchedule installment : installments) {
            verify(invoiceService, times(1)).findOrGenerateInvoiceForSchedule(installment.getBillingScheduleId());
            verify(taxService, times(1)).findOrCalculateTaxForSchedule(installment.getBillingScheduleId());
        }
        assertThat(runRepository.findAll()).hasSize(3)
                .allSatisfy(run -> assertThat(run.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL));
    }

    @Test
    void recurring_isProcessedOnce_andRepeatedProcessingDoesNothing() {
        configurationIs(BillingType.RETAINER);
        BillingSchedule october = occurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.TAX_PENDING);
        UUID runId = occurrenceRun(october);

        orchestrator.processRun(runId);
        AutomaticBillingRun second = orchestrator.processRun(runId);

        assertThat(second.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        assertThat(second.getAttemptCount()).isEqualTo(1);
        verify(taxService, times(1)).findOrCalculateTaxForSchedule(october.getBillingScheduleId());
        verify(invoiceService, times(1)).findOrGenerateInvoiceForSchedule(october.getBillingScheduleId());
        verify(invoiceService, times(1)).submitForApproval(invoiceId);
    }

    @Test
    void occurrenceDeletedSinceTheRunWasCreated_failsTheRun() {
        configurationIs(BillingType.FIXED_PRICE);
        BillingSchedule occurrence = occurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.TAX_PENDING);
        UUID runId = occurrenceRun(occurrence);
        tx.executeWithoutResult(status -> scheduleRepository.deleteById(occurrence.getBillingScheduleId()));

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(result.getFailureReason()).contains("no longer exists");
        verifyNoInteractions(taxService);
    }

    // ================================================================ tax & invoice

    @Test
    void taxFailure_failsAtTaxCalculationAndCreatesNoInvoice() {
        configurationIs(BillingType.FIXED_PRICE);
        BillingSchedule occurrence = occurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.TAX_PENDING);
        doThrow(new GlobalExceptionHandler.ValidationException(
                "No active tax configuration is available for the selected tax region and billing period."))
                .when(taxService).findOrCalculateTaxForSchedule(occurrence.getBillingScheduleId());

        AutomaticBillingRun result = orchestrator.processRun(occurrenceRun(occurrence));

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(result.getFailureStep()).isEqualTo(AutomaticBillingStep.TAX_CALCULATION);
        assertThat(result.getCurrentStep()).isEqualTo(AutomaticBillingStep.TAX_CALCULATION);
        assertThat(result.getLastCompletedStep()).isEqualTo(AutomaticBillingStep.SOURCE_DATA_ACQUISITION);
        assertThat(result.getFailureReason()).contains("No active tax configuration");
        verify(invoiceService, never()).findOrGenerateInvoiceForSchedule(any());
    }

    @Test
    void invoiceFailure_failsAtInvoiceCreationAndNeverSubmits() {
        configurationIs(BillingType.FIXED_PRICE);
        BillingSchedule occurrence = occurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.TAX_PENDING);
        when(invoiceService.findOrGenerateInvoiceForSchedule(occurrence.getBillingScheduleId()))
                .thenThrow(new GlobalExceptionHandler.ResourceNotFoundException("No active company profile."));

        AutomaticBillingRun result = orchestrator.processRun(occurrenceRun(occurrence));

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(result.getFailureStep()).isEqualTo(AutomaticBillingStep.INVOICE_CREATION);
        assertThat(result.getFailureReason()).isEqualTo("No active company profile.");
        assertThat(result.getInvoiceId()).isNull();
        verify(invoiceService, never()).submitForApproval(any());
    }

    @Test
    void existingInvoiceAlreadyPendingApproval_isReusedAndNotSubmittedAgain() {
        configurationIs(BillingType.FIXED_PRICE);
        stubInvoice(InvoiceStatus.PENDING_APPROVAL);
        BillingSchedule occurrence = occurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.INVOICED);

        AutomaticBillingRun result = orchestrator.processRun(
                tx.execute(status -> runRepository.save(AutomaticBillingRun.builder()
                        .billingConfiguration(configuration)
                        .billingScheduleId(occurrence.getBillingScheduleId())
                        .billingPeriodStart(occurrence.getPeriodStartDate())
                        .billingPeriodEnd(occurrence.getPeriodEndDate())
                        .build())).getAutomaticBillingRunId());

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        assertThat(result.getInvoiceId()).isEqualTo(invoiceId);
        verify(invoiceService, never()).submitForApproval(any());
    }

    @Test
    void invoiceAlreadyApprovedAndFinalized_completesTheRunWithoutSubmittingOrSending() {
        configurationIs(BillingType.FIXED_PRICE);
        stubInvoice(InvoiceStatus.READY_TO_SEND);
        UUID runId = occurrenceRun(occurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.TAX_PENDING));

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.COMPLETED);
        verify(invoiceService, never()).submitForApproval(any());
        verify(invoiceService, never()).approveInvoice(any());
        verify(invoiceService, never()).finalizeInvoiceDocument(any());
    }

    @Test
    void automaticBilling_neverApprovesOrFinalizesTheInvoice() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        verify(invoiceService, never()).approveInvoice(any());
        verify(invoiceService, never()).finalizeInvoiceDocument(any());
    }

    @Test
    void invoiceAlreadyApprovedManually_completesTheRun() {
        configurationIs(BillingType.FIXED_PRICE);
        stubInvoice(InvoiceStatus.APPROVED);
        UUID runId = occurrenceRun(occurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.TAX_PENDING));

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isNotNull();
        verify(invoiceService, never()).submitForApproval(any());
    }

    @Test
    void rejectedInvoice_isNotResubmittedAutomatically() {
        configurationIs(BillingType.FIXED_PRICE);
        stubInvoice(InvoiceStatus.REJECTED);
        UUID runId = occurrenceRun(occurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.TAX_PENDING));

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(result.getFailureStep()).isEqualTo(AutomaticBillingStep.SUBMIT_FOR_APPROVAL);
        assertThat(result.getFailureReason()).contains("was rejected");
        verify(invoiceService, never()).submitForApproval(any());
    }

    // ================================================================ resume

    @Test
    void resumedAfterSourceData_continuesWithTax() {
        configurationIs(BillingType.FIXED_PRICE);
        BillingSchedule occurrence = occurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.SCHEDULED);
        UUID runId = occurrenceRun(occurrence);
        interruptedAfter(runId, AutomaticBillingStep.SOURCE_DATA_ACQUISITION, null, null);

        orchestrator.processRun(runId);

        verifyNoInteractions(occurrenceTransitions);
        verify(taxService).findOrCalculateTaxForSchedule(occurrence.getBillingScheduleId());
    }

    @Test
    void resumedAfterTax_continuesWithInvoiceCreation() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        interruptedAfter(runId, AutomaticBillingStep.TAX_CALCULATION, snapshotId, null);

        AutomaticBillingRun result = orchestrator.processRun(runId);

        verifyNoInteractions(snapshotService, taxService);
        verify(invoiceService).findOrGenerateInvoice(snapshotId);
        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
    }

    @Test
    void resumedAfterInvoiceCreation_onlySubmitsForApproval() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        interruptedAfter(runId, AutomaticBillingStep.INVOICE_CREATION, snapshotId, invoiceId);

        AutomaticBillingRun result = orchestrator.processRun(runId);

        verifyNoInteractions(snapshotService, taxService);
        verify(invoiceService, never()).findOrGenerateInvoice(any());
        verify(invoiceService).submitForApproval(invoiceId);
        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
    }

    @Test
    void pendingApprovalRun_isNotProcessedAgain() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        orchestrator.processRun(runId);
        reset(snapshotService, taxService, invoiceService);

        AutomaticBillingRun again = orchestrator.processRun(runId);

        assertThat(again.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        assertThat(again.getAttemptCount()).isEqualTo(1);
        verifyNoInteractions(snapshotService, taxService, invoiceService);
    }

    // ================================================================ retry (Phase 11)

    @Test
    void temporaryTmsFailure_isScheduledForRetryWithBackoff() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        when(snapshotService.createBillingSnapshot(any()))
                .thenThrow(new TmsServiceUnavailableException("TMS service failed with status 503."));
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(result.getFailureStep()).isEqualTo(AutomaticBillingStep.SOURCE_DATA_ACQUISITION);
        assertThat(result.getNextRetryAt()).isEqualTo(LocalDateTime.now(CLOCK).plusMinutes(15));
    }

    @Test
    void validationFailure_isNotScheduledForRetry() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        when(snapshotService.createBillingSnapshot(any()))
                .thenReturn(ApiResponse.failure("Timesheet [TS-9] is not approved."));
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(result.getNextRetryAt()).isNull();
    }

    @Test
    void temporaryFailureOnTheLastAttempt_isNotScheduledAgain() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        when(taxService.findOrCalculateTax(snapshotId))
                .thenThrow(new org.springframework.dao.CannotAcquireLockException("Lock wait timeout exceeded"));
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        AutomaticBillingRun first = orchestrator.processRun(runId);
        assertThat(first.getNextRetryAt()).isEqualTo(LocalDateTime.now(CLOCK).plusMinutes(15));
        dueForRetry(runId);
        AutomaticBillingRun second = orchestrator.processRun(runId);
        assertThat(second.getNextRetryAt()).isEqualTo(LocalDateTime.now(CLOCK).plusMinutes(30));
        dueForRetry(runId);
        AutomaticBillingRun third = orchestrator.processRun(runId);

        assertThat(third.getAttemptCount()).isEqualTo(3);
        assertThat(third.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(third.getNextRetryAt()).isNull();
        // The snapshot was acquired once; only the failing step was repeated.
        verify(snapshotService, times(1)).createBillingSnapshot(any());
        verify(taxService, times(3)).findOrCalculateTax(snapshotId);
        verifyNoInteractions(invoiceService);
    }

    @Test
    void retryAfterInvoiceCreation_onlyResubmits_andNeverCreatesAnotherInvoice() {
        configurationIs(BillingType.FIXED_PRICE);
        BillingSchedule occurrence = occurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.TAX_PENDING);
        when(invoiceService.submitForApproval(invoiceId))
                .thenThrow(new org.springframework.dao.QueryTimeoutException("timeout"))
                .thenReturn(invoice(InvoiceStatus.PENDING_APPROVAL));
        UUID runId = occurrenceRun(occurrence);

        AutomaticBillingRun failed = orchestrator.processRun(runId);
        assertThat(failed.getNextRetryAt()).isNotNull();
        assertThat(failed.getInvoiceId()).isEqualTo(invoiceId);
        dueForRetry(runId);
        AutomaticBillingRun retried = orchestrator.processRun(runId);

        assertThat(retried.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        verify(taxService, times(1)).findOrCalculateTaxForSchedule(occurrence.getBillingScheduleId());
        verify(invoiceService, times(1)).findOrGenerateInvoiceForSchedule(occurrence.getBillingScheduleId());
        verify(invoiceService, times(2)).submitForApproval(invoiceId);
        verify(invoiceService, never()).approveInvoice(any());
        verify(invoiceService, never()).finalizeInvoiceDocument(any());
    }

    @Test
    void activeWorker_renewsItsClaimBeforeEveryStep() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        AutomaticBillingRunService spied = org.mockito.Mockito.spy(runService);
        AutomaticBillingOrchestrator watched = new AutomaticBillingOrchestrator(spied, configurationIntegration,
                scheduleRepository, occurrenceTransitions, occurrenceService, snapshotService, taxService,
                invoiceService, retryPolicy, Duration.ofMinutes(15));

        watched.processRun(runId);

        verify(spied, times(4)).renewClaim(eq(runId), any(), eq(Duration.ofMinutes(15)));
    }

    @Test
    void interruptedRun_isResumedFromItsLastCompletedStep_onceItsClaimHasExpired() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        processingWithClaimExpiring(runId, LocalDateTime.now(CLOCK).minusMinutes(1));

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        assertThat(result.getAttemptCount()).isEqualTo(2);
        verifyNoInteractions(snapshotService);
        verify(taxService, never()).findOrCalculateTax(any());
        verify(invoiceService).findOrGenerateInvoice(snapshotId);
    }

    @Test
    void runStillClaimedByAnActiveWorker_isNotTakenOver() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        processingWithClaimExpiring(runId, LocalDateTime.now(CLOCK).plusMinutes(5));

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.PROCESSING);
        assertThat(result.getAttemptCount()).isEqualTo(1);
        verifyNoInteractions(snapshotService, taxService, invoiceService);
    }

    @Test
    void interruptedRunWithoutAttemptsLeft_isClosedAsFailed_butAnActiveOneNever() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        UUID expired = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        processingWithClaimExpiring(expired, LocalDateTime.now(CLOCK).minusMinutes(1));
        UUID active = timeAndMaterialRun(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        processingWithClaimExpiring(active, LocalDateTime.now(CLOCK).plusMinutes(1));

        assertThat(runService.failInterruptedRun(expired, "Interrupted; no automatic attempt left.")).isPresent();
        assertThat(runService.failInterruptedRun(active, "Interrupted; no automatic attempt left.")).isEmpty();

        AutomaticBillingRun closed = stored(expired);
        assertThat(closed.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(closed.getFailureStep()).isEqualTo(AutomaticBillingStep.INVOICE_CREATION);
        assertThat(closed.getNextRetryAt()).isNull();
        assertThat(closed.getClaimToken()).isNull();
        assertThat(closed.getBillingSnapshotId()).isEqualTo(snapshotId);
        assertThat(stored(active).getStatus()).isEqualTo(AutomaticBillingRunStatus.PROCESSING);
    }

    /** Makes a FAILED run's scheduled retry due now. */
    private void dueForRetry(UUID runId) {
        tx.executeWithoutResult(status -> {
            AutomaticBillingRun run = runRepository.findById(runId).orElseThrow();
            run.setNextRetryAt(LocalDateTime.now(CLOCK));
            runRepository.save(run);
        });
    }

    /** A run a worker was processing (snapshot and tax done, invoice in progress) when it stopped. */
    private void processingWithClaimExpiring(UUID runId, LocalDateTime claimExpiresAt) {
        tx.executeWithoutResult(status -> {
            AutomaticBillingRun run = runRepository.findById(runId).orElseThrow();
            run.setStatus(AutomaticBillingRunStatus.PROCESSING);
            run.setAttemptCount(1);
            run.setClaimToken(UUID.randomUUID());
            run.setClaimExpiresAt(claimExpiresAt);
            run.setBillingSnapshotId(snapshotId);
            run.setLastCompletedStep(AutomaticBillingStep.TAX_CALCULATION);
            run.setCurrentStep(AutomaticBillingStep.INVOICE_CREATION);
            runRepository.save(run);
        });
    }

    @Test
    void failedRunNotYetDueForRetry_isLeftAlone() {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        tx.executeWithoutResult(status -> {
            AutomaticBillingRun run = runRepository.findById(runId).orElseThrow();
            run.setStatus(AutomaticBillingRunStatus.FAILED);
            run.setNextRetryAt(LocalDateTime.now(CLOCK).plusHours(1));
            runRepository.save(run);
        });

        AutomaticBillingRun result = orchestrator.processRun(runId);

        assertThat(result.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        verifyNoInteractions(snapshotService, taxService, invoiceService);
    }

    // ================================================================ concurrency

    @Test
    void twoConcurrentCalls_onlyOneProcessesTheRun() throws Exception {
        configurationIs(BillingType.TIME_AND_MATERIAL);
        UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        CountDownLatch firstWorkerInsideTms = new CountDownLatch(1);
        CountDownLatch releaseFirstWorker = new CountDownLatch(1);
        when(snapshotService.createBillingSnapshot(any())).thenAnswer(invocation -> {
            firstWorkerInsideTms.countDown();
            releaseFirstWorker.await(30, TimeUnit.SECONDS);
            return ApiResponse.success("created", BillingSnapshotResponseDto.builder().snapshotId(snapshotId).build());
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<AutomaticBillingRun> first = pool.submit(() -> orchestrator.processRun(runId));
            assertThat(firstWorkerInsideTms.await(30, TimeUnit.SECONDS)).isTrue();

            // The first worker owns the run and is mid-acquisition.
            AutomaticBillingRun second = pool.submit(() -> orchestrator.processRun(runId)).get(30, TimeUnit.SECONDS);
            assertThat(second.getStatus()).isEqualTo(AutomaticBillingRunStatus.PROCESSING);

            releaseFirstWorker.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS).getStatus())
                    .isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        } finally {
            pool.shutdownNow();
        }

        verify(snapshotService, times(1)).createBillingSnapshot(any());
        verify(taxService, times(1)).findOrCalculateTax(snapshotId);
        verify(invoiceService, times(1)).submitForApproval(invoiceId);
        assertThat(stored(runId).getAttemptCount()).isEqualTo(1);
    }

    @Test
    void manyConcurrentCalls_processTheRunExactlyOnce() throws Exception {
        configurationIs(BillingType.RETAINER);
        BillingSchedule occurrence = occurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                BillingPeriodStatus.TAX_PENDING);
        UUID runId = occurrenceRun(occurrence);

        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<AutomaticBillingRun>> calls = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                calls.add(pool.submit(() -> {
                    start.await();
                    return orchestrator.processRun(runId);
                }));
            }
            start.countDown();
            for (Future<AutomaticBillingRun> call : calls) {
                call.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        verify(invoiceService, times(1)).findOrGenerateInvoiceForSchedule(occurrence.getBillingScheduleId());
        verify(invoiceService, times(1)).submitForApproval(invoiceId);
        assertThat(stored(runId).getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        assertThat(stored(runId).getAttemptCount()).isEqualTo(1);
    }

    // ================================================================ operations (Phase 13)

    @Test
    void everyRun_logsOneTraceableOutcome_withoutSecrets_andCountsTheFailureByStep(
            org.springframework.boot.test.system.CapturedOutput output) {
        io.micrometer.core.instrument.simple.SimpleMeterRegistry meters =
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        io.micrometer.core.instrument.Metrics.addRegistry(meters);
        try {
            configurationIs(BillingType.TIME_AND_MATERIAL);
            when(snapshotService.createBillingSnapshot(any())).thenThrow(new TmsServiceUnavailableException(
                    "TMS service failed with status 503. Authorization: Bearer eyJhbGciOi.eyJzdWIiOi.c2lnbmF0dXJl"));
            UUID runId = timeAndMaterialRun(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

            orchestrator.processRun(runId);

            String outcome = output.getOut().lines()
                    .filter(line -> line.contains("[AutomaticBillingRunOutcome]"))
                    .reduce((first, second) -> second).orElseThrow();
            assertThat(outcome)
                    .contains("[run=" + runId + "]")
                    .contains("run=" + runId, "configuration=" + configuration.getBillingConfigurationId(),
                            "occurrence=-", "type=TIME_AND_MATERIAL", "period=2026-10-01..2026-10-31",
                            "status=FAILED", "failureStep=SOURCE_DATA_ACQUISITION", "attempt=1",
                            "nextRetryAt=2026-11-01T10:15");
            // Every line logged during the run carries its id; none carries the token.
            assertThat(output.getOut()).doesNotContain("eyJhbGciOi").contains("reason=TMS service failed with status 503. Authorization: [REDACTED]");
            assertThat(org.slf4j.MDC.get("billingRunId")).isNull();
            assertThat(meters.get("ar.automatic_billing.failures")
                    .tag("step", "SOURCE_DATA_ACQUISITION").tag("retryable", "true").counter().count())
                    .isEqualTo(1.0);
        } finally {
            io.micrometer.core.instrument.Metrics.removeRegistry(meters);
        }
    }
}
