package com.AccountReceivableManagement.service_Imple.invoice_generation;

import com.AccountReceivableManagement.dto.company_profile.CompanyProfileResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshotItem;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.invoice_generation.Invoice;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.tax_calculation.TaxCalculation;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxCalculationStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceApprovalHistoryRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.PaymentTermsMasterRepository;
import com.AccountReceivableManagement.repo.tax_calculation.TaxCalculationRepository;
import com.AccountReceivableManagement.service_interface.company_profile.CompanyProfileService;
import com.AccountReceivableManagement.service_interface.concurrency_approval.RecordActionLockService;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Invoice selection when one Time &amp; Material billing period was acquired in several batches (incremental
 * billing): every batch is its own Billing Snapshot and is invoiced on its own, by snapshot id. The two
 * period-keyed lookups in {@link InvoiceServiceImpl} - the schedule-based invoice path and the invoice
 * generation workspace - must never silently pick one batch for another.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplMultiBatchTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2026, 11, 4);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 11, 10);

    @Mock
    private com.AccountReceivableManagement.service_Imple.projectbilling_config.BillingOccurrenceServiceImpl billingOccurrenceService;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceApprovalHistoryRepository invoiceApprovalHistoryRepository;
    @Mock private BillingSnapshotRepository billingSnapshotRepository;
    @Mock private TaxCalculationRepository taxCalculationRepository;
    @Mock private BillingConfigurationService billingConfigurationService;
    @Mock private PaymentTermsMasterRepository paymentTermsMasterRepository;
    @Mock private BillingScheduleRepository billingScheduleRepository;
    @Mock private CompanyProfileService companyProfileService;
    @Mock private RecordActionLockService recordActionLockService;

    @InjectMocks
    private InvoiceServiceImpl invoiceService;

    private final UUID clientId = UUID.randomUUID();
    private final UUID configurationId = UUID.randomUUID();
    private BillingConfiguration billingConfiguration;

    @BeforeEach
    void setUp() {
        billingConfiguration = BillingConfiguration.builder()
                .billingConfigurationId(configurationId)
                .client(Client.builder().clientId(clientId).build())
                .project(ProjectMasterReference.builder().pmsProjectId(23L).build())
                .build();
        lenient().when(companyProfileService.getActive()).thenReturn(CompanyProfileResponseDto.builder()
                .legalName("Paves Technologies Pvt Ltd").isActive(true).build());
        lenient().when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(billingScheduleRepository.save(any(BillingSchedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(billingSnapshotRepository.save(any(BillingSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ================================================================ fixtures

    /** A TAX_COMPLETED batch of the period, holding one timesheet line. */
    private BillingSnapshot batch(int batchSequence, String timesheetId, String amount) {
        BillingSnapshot snapshot = BillingSnapshot.builder()
                .id(UUID.randomUUID())
                .snapshotNumber("BS-BATCH-" + batchSequence)
                .billingConfigurationId(configurationId)
                .clientId(clientId)
                .projectId(23L)
                .currencyCode("INR")
                .billingType("Time & Material")
                .billingPeriodStart(PERIOD_START)
                .billingPeriodEnd(PERIOD_END)
                .batchSequence(batchSequence)
                .status(BillingSnapshotStatus.TAX_COMPLETED)
                .subtotal(new BigDecimal(amount))
                .expenseAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal(amount))
                .createdDate(LocalDateTime.of(2026, 11, 20 + batchSequence, 10, 0))
                .build();
        snapshot.getItems().add(BillingSnapshotItem.builder()
                .billingSnapshotItemId(UUID.randomUUID())
                .billingSnapshot(snapshot)
                .itemType(BillingItemType.TIME_ENTRY)
                .itemName("Resource " + timesheetId)
                .sourceReferenceId(timesheetId)
                .billedSourceReferenceId(timesheetId)
                .quantity(BigDecimal.ONE)
                .rate(new BigDecimal(amount))
                .amount(new BigDecimal(amount))
                .workDate(PERIOD_START)
                .build());
        return snapshot;
    }

    private TaxCalculation snapshotTax(BillingSnapshot snapshot) {
        return TaxCalculation.builder()
                .taxCalculationId(UUID.randomUUID())
                .billingSnapshotId(snapshot.getId())
                .taxRegionId(UUID.randomUUID())
                .taxConfigurationId(UUID.randomUUID())
                .taxableAmount(snapshot.getTotalAmount())
                .totalTaxAmount(BigDecimal.ZERO)
                .grandTotal(snapshot.getTotalAmount())
                .status(TaxCalculationStatus.CALCULATED)
                .components(new ArrayList<>())
                .build();
    }

    private BillingSchedule timeAndMaterialOccurrence(String amount) {
        return BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .billingConfiguration(billingConfiguration)
                .periodNumber(14)
                .periodStartDate(PERIOD_START)
                .periodEndDate(PERIOD_END)
                .billingDate(PERIOD_END)
                .billingAmount(new BigDecimal(amount))
                .periodStatus(BillingPeriodStatus.TAX_CALCULATED)
                .taxStatus(BillingPeriodStatus.TAX_CALCULATED)
                .isInvoiced(false)
                .isActive(true)
                .build();
    }

    private TaxCalculation scheduleTax(BillingSchedule schedule) {
        return TaxCalculation.builder()
                .taxCalculationId(UUID.randomUUID())
                .billingScheduleId(schedule.getBillingScheduleId())
                .taxRegionId(UUID.randomUUID())
                .taxConfigurationId(UUID.randomUUID())
                .taxableAmount(schedule.getBillingAmount())
                .totalTaxAmount(BigDecimal.ZERO)
                .grandTotal(schedule.getBillingAmount())
                .status(TaxCalculationStatus.CALCULATED)
                .components(new ArrayList<>())
                .build();
    }

    private BillingConfigurationResponseDto timeAndMaterialConfiguration() {
        return BillingConfigurationResponseDto.builder()
                .clientId(clientId)
                .clientName("Account Management")
                .projectId(23L)
                .projectName("Website Redesign")
                .currencyCode("INR")
                .billingTypeName("Time & Material")
                .billingFrequencyName("Weekly")
                .build();
    }

    private void stubScheduleInvoice(BillingSchedule schedule, List<BillingSnapshot> batchesOfThePeriod) {
        when(billingScheduleRepository.findByIdForUpdate(schedule.getBillingScheduleId()))
                .thenReturn(Optional.of(schedule));
        when(taxCalculationRepository.findByBillingScheduleId(schedule.getBillingScheduleId()))
                .thenReturn(Optional.of(scheduleTax(schedule)));
        when(billingConfigurationService.getBillingConfiguration(configurationId))
                .thenReturn(timeAndMaterialConfiguration());
        when(billingSnapshotRepository.findAllByBillingConfigurationIdAndBillingPeriodStartAndBillingPeriodEnd(
                configurationId, PERIOD_START, PERIOD_END)).thenReturn(batchesOfThePeriod);
    }

    private void stubSnapshotInvoice(BillingSnapshot snapshot) {
        when(billingSnapshotRepository.findById(snapshot.getId())).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.findByBillingSnapshotId(snapshot.getId()))
                .thenReturn(Optional.of(snapshotTax(snapshot)));
        lenient().when(billingConfigurationService.getBillingConfiguration(configurationId))
                .thenReturn(timeAndMaterialConfiguration());
    }

    /** Frozen state of a snapshot: status, totals and its claimed lines. */
    private static String fingerprint(BillingSnapshot snapshot) {
        return snapshot.getId() + "|" + snapshot.getStatus() + "|" + snapshot.getBatchSequence() + "|"
                + snapshot.getTotalAmount() + "|" + snapshot.getItems().stream()
                .map(item -> item.getBilledSourceReferenceId() + ":" + item.getAmount()).toList();
    }

    // ================================================================ schedule-based T&M invoice path

    // 1. One batch in the period: unchanged behaviour - that batch is the occurrence's snapshot.
    @Test
    void scheduleInvoice_periodWithOneBatch_invoicesThatBatchAsBefore() {
        BillingSchedule schedule = timeAndMaterialOccurrence("12000.00");
        BillingSnapshot only = batch(1, "TS-1", "12000.00");
        stubScheduleInvoice(schedule, List.of(only));
        String before = fingerprint(only);

        InvoiceResponseDto response = invoiceService.generateInvoiceForSchedule(schedule.getBillingScheduleId());

        assertThat(response.getBillingSnapshotId()).isEqualTo(only.getId());
        assertThat(response.getBillingSnapshotNumber()).isEqualTo("BS-BATCH-1");
        assertThat(schedule.getIsInvoiced()).isTrue();
        // The schedule path never writes the snapshot.
        verify(billingSnapshotRepository, never()).save(any());
        assertThat(fingerprint(only)).isEqualTo(before);
    }

    // 2 + 3. Several batches: a controlled, meaningful refusal - never a silent pick of one batch.
    @Test
    void scheduleInvoice_periodWithSeveralBatches_isRefusedWithoutSelectingAny() {
        BillingSchedule schedule = timeAndMaterialOccurrence("12000.00");
        BillingSnapshot first = batch(1, "TS-1", "12000.00");
        BillingSnapshot second = batch(2, "TS-3", "3000.00");
        stubScheduleInvoice(schedule, List.of(first, second));
        String firstBefore = fingerprint(first);
        String secondBefore = fingerprint(second);

        assertThatThrownBy(() -> invoiceService.generateInvoiceForSchedule(schedule.getBillingScheduleId()))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("acquired in 2 billing batches")
                .hasMessageContaining("generate each batch's invoice from its own Billing Snapshot");

        verify(invoiceRepository, never()).save(any());
        verify(billingScheduleRepository, never()).save(any());
        verify(billingSnapshotRepository, never()).save(any());
        assertThat(schedule.getIsInvoiced()).isFalse();
        assertThat(fingerprint(first)).isEqualTo(firstBefore);
        assertThat(fingerprint(second)).isEqualTo(secondBefore);
    }

    @Test
    void scheduleInvoice_periodWithoutAnyBatch_stillReportsTheSnapshotAsMissing() {
        BillingSchedule schedule = timeAndMaterialOccurrence("12000.00");
        stubScheduleInvoice(schedule, List.of());

        assertThatThrownBy(() -> invoiceService.generateInvoiceForSchedule(schedule.getBillingScheduleId()))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessageContaining("Billing Snapshot not found for Time & Material billing occurrence");
        verify(invoiceRepository, never()).save(any());
    }

    // ================================================================ invoice by snapshot id

    // 4 + 5. Each batch is invoiced by its own id; invoicing one never touches the other.
    @Test
    void invoiceBySnapshotId_eachBatchGetsItsOwnInvoice_otherBatchUnchanged() {
        BillingSnapshot first = batch(1, "TS-1", "12000.00");
        BillingSnapshot second = batch(2, "TS-3", "3000.00");
        stubSnapshotInvoice(first);
        stubSnapshotInvoice(second);
        String secondBefore = fingerprint(second);

        InvoiceResponseDto firstInvoice = invoiceService.generateInvoice(first.getId());

        assertThat(firstInvoice.getBillingSnapshotId()).isEqualTo(first.getId());
        assertThat(firstInvoice.getGrandTotal()).isEqualByComparingTo("12000.00");
        assertThat(first.getStatus()).isEqualTo(BillingSnapshotStatus.INVOICED);
        assertThat(fingerprint(second)).isEqualTo(secondBefore);

        String firstAfterInvoicing = fingerprint(first);
        InvoiceResponseDto secondInvoice = invoiceService.generateInvoice(second.getId());

        assertThat(secondInvoice.getBillingSnapshotId()).isEqualTo(second.getId());
        assertThat(secondInvoice.getGrandTotal()).isEqualByComparingTo("3000.00");
        assertThat(secondInvoice.getInvoiceNumber()).isNotEqualTo(firstInvoice.getInvoiceNumber());
        assertThat(secondInvoice.getItems()).extracting(item -> item.getSourceReferenceId())
                .containsExactly("TS-3");
        // The earlier batch and its invoice are left exactly as they were.
        assertThat(fingerprint(first)).isEqualTo(firstAfterInvoicing);

        ArgumentCaptor<BillingSnapshot> saved = ArgumentCaptor.forClass(BillingSnapshot.class);
        verify(billingSnapshotRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(BillingSnapshot::getId)
                .containsExactly(first.getId(), second.getId());
        ArgumentCaptor<Invoice> invoices = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository, org.mockito.Mockito.times(2)).save(invoices.capture());
        assertThat(invoices.getAllValues()).extracting(Invoice::getBillingSnapshotId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void invoiceBySnapshotId_batchAlreadyInvoiced_isRefused_whileTheNewBatchIsStillInvoiceable() {
        BillingSnapshot first = batch(1, "TS-1", "12000.00");
        first.setStatus(BillingSnapshotStatus.INVOICED);
        BillingSnapshot second = batch(2, "TS-3", "3000.00");
        when(billingSnapshotRepository.findById(first.getId())).thenReturn(Optional.of(first));
        when(invoiceRepository.existsByBillingSnapshotId(first.getId())).thenReturn(true);
        stubSnapshotInvoice(second);
        String firstBefore = fingerprint(first);

        assertThatThrownBy(() -> invoiceService.generateInvoice(first.getId()))
                .isInstanceOf(GlobalExceptionHandler.DuplicateResourceException.class);
        InvoiceResponseDto secondInvoice = invoiceService.generateInvoice(second.getId());

        assertThat(secondInvoice.getBillingSnapshotId()).isEqualTo(second.getId());
        assertThat(fingerprint(first)).isEqualTo(firstBefore);
        verify(billingSnapshotRepository, never()).save(first);
    }

    // ================================================================ invoice generation workspace

    @Test
    void workspace_periodWithSeveralBatches_listsEachBatchOnce_andNotTheScheduleRow() {
        BillingSnapshot first = batch(1, "TS-1", "12000.00");
        BillingSnapshot second = batch(2, "TS-3", "3000.00");
        BillingSchedule schedule = timeAndMaterialOccurrence("12000.00");
        when(invoiceRepository.findAllByOrderByGeneratedAtDesc()).thenReturn(List.of());
        when(billingSnapshotRepository.findAllById(any())).thenReturn(List.of());
        when(billingSnapshotRepository.findAllByStatusOrderByCreatedDateDesc(BillingSnapshotStatus.TAX_COMPLETED))
                .thenReturn(List.of(second, first));
        when(billingScheduleRepository.findByPeriodStatusAndTaxStatusAndIsActiveTrue(
                BillingPeriodStatus.TAX_CALCULATED, BillingPeriodStatus.TAX_CALCULATED))
                .thenReturn(List.of(schedule));
        lenient().when(taxCalculationRepository.findByBillingSnapshotId(first.getId()))
                .thenReturn(Optional.of(snapshotTax(first)));
        lenient().when(taxCalculationRepository.findByBillingSnapshotId(second.getId()))
                .thenReturn(Optional.of(snapshotTax(second)));
        lenient().when(billingConfigurationService.getBillingConfiguration(any()))
                .thenReturn(timeAndMaterialConfiguration());
        when(billingSnapshotRepository.findAllByBillingConfigurationIdAndBillingPeriodStartAndBillingPeriodEnd(
                configurationId, PERIOD_START, PERIOD_END)).thenReturn(List.of(first, second));

        var response = invoiceService.getInvoiceGenerationWorkspace();

        assertThat(response.getRows()).extracting(row -> row.getSnapshotId())
                .containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(response.getRows()).allMatch(row -> row.getBillingScheduleId() == null);
        assertThat(response.getSummary().getReadyForInvoiceCount()).isEqualTo(2);
        verify(invoiceRepository, never()).save(any());
        verify(billingSnapshotRepository, never()).save(any());
    }
}
