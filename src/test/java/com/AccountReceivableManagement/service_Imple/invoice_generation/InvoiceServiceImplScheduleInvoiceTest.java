package com.AccountReceivableManagement.service_Imple.invoice_generation;

import com.AccountReceivableManagement.dto.company_profile.CompanyProfileResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceItemResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.invoice_generation.Invoice;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingMilestonePlan;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingPaymentEntry;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingRecurringConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.tax_calculation.TaxCalculation;
import com.AccountReceivableManagement.entity.tax_calculation.TaxCalculationComponent;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.PaymentStructure;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxCalculationStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceApprovalHistoryRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingMilestonePlanRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingPaymentEntryRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.PaymentTermsMasterRepository;
import com.AccountReceivableManagement.repo.tax_calculation.TaxCalculationRepository;
import com.AccountReceivableManagement.service_interface.company_profile.CompanyProfileService;
import com.AccountReceivableManagement.service_interface.concurrency_approval.RecordActionLockService;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 1 (automation readiness) behaviour of occurrence invoices:
 * source-specific line items for Fixed Price / Milestone / Installment /
 * Recurring, find-or-generate idempotency, and failure atomicity.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplScheduleInvoiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceApprovalHistoryRepository invoiceApprovalHistoryRepository;
    @Mock private BillingSnapshotRepository billingSnapshotRepository;
    @Mock private TaxCalculationRepository taxCalculationRepository;
    @Mock private BillingConfigurationService billingConfigurationService;
    @Mock private PaymentTermsMasterRepository paymentTermsMasterRepository;
    @Mock private BillingScheduleRepository billingScheduleRepository;
    @Mock private CompanyProfileService companyProfileService;
    @Mock private RecordActionLockService recordActionLockService;
    @Mock private BillingMilestonePlanRepository billingMilestonePlanRepository;
    @Mock private BillingPaymentEntryRepository billingPaymentEntryRepository;

    @InjectMocks
    private InvoiceServiceImpl invoiceService;

    private final UUID clientId = UUID.randomUUID();

    private BillingConfiguration billingConfiguration;

    @BeforeEach
    void setUp() {
        billingConfiguration = BillingConfiguration.builder()
                .billingConfigurationId(UUID.randomUUID())
                .client(Client.builder().clientId(clientId).build())
                .project(ProjectMasterReference.builder().pmsProjectId(23L).build())
                .build();

        lenient().when(companyProfileService.getActive()).thenReturn(CompanyProfileResponseDto.builder()
                .legalName("Paves Technologies Pvt Ltd").isActive(true).build());
        lenient().when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(billingScheduleRepository.save(any(BillingSchedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private BillingSchedule occurrence(int periodNumber, LocalDate start, LocalDate end, String amount) {
        return BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .billingConfiguration(billingConfiguration)
                .periodNumber(periodNumber)
                .periodStartDate(start)
                .periodEndDate(end)
                .billingDate(end)
                .billingAmount(new BigDecimal(amount))
                .periodStatus(BillingPeriodStatus.TAX_CALCULATED)
                .taxStatus(BillingPeriodStatus.TAX_CALCULATED)
                .isInvoiced(false)
                .isActive(true)
                .build();
    }

    private TaxCalculation tax(BillingSchedule schedule) {
        BigDecimal taxable = schedule.getBillingAmount();
        return TaxCalculation.builder()
                .taxCalculationId(UUID.randomUUID())
                .billingScheduleId(schedule.getBillingScheduleId())
                .taxRegionId(UUID.randomUUID())
                .taxConfigurationId(UUID.randomUUID())
                .taxableAmount(taxable)
                .totalTaxAmount(BigDecimal.ZERO)
                .grandTotal(taxable)
                .status(TaxCalculationStatus.CALCULATED)
                .components(new ArrayList<>())
                .build();
    }

    private BillingConfigurationResponseDto configuration(String billingTypeName, String frequencyName) {
        return BillingConfigurationResponseDto.builder()
                .clientId(clientId)
                .clientName("Account Management")
                .projectId(23L)
                .projectName("Website Redesign")
                .currencyCode("INR")
                .billingTypeName(billingTypeName)
                .billingFrequencyName(frequencyName)
                .build();
    }

    private InvoiceItemResponseDto generateSingleLine(BillingSchedule schedule,
            BillingConfigurationResponseDto configuration) {
        when(billingScheduleRepository.findByIdForUpdate(schedule.getBillingScheduleId()))
                .thenReturn(Optional.of(schedule));
        when(taxCalculationRepository.findByBillingScheduleId(schedule.getBillingScheduleId()))
                .thenReturn(Optional.of(tax(schedule)));
        when(billingConfigurationService.getBillingConfiguration(any())).thenReturn(configuration);

        InvoiceResponseDto response = invoiceService.generateInvoiceForSchedule(schedule.getBillingScheduleId());

        assertThat(response.getItems()).hasSize(1);
        InvoiceItemResponseDto line = response.getItems().get(0);
        assertThat(line.getQuantity()).isEqualByComparingTo("1");
        assertThat(line.getAmount()).isEqualByComparingTo(schedule.getBillingAmount());
        assertThat(line.getSourceReferenceId()).isEqualTo(schedule.getBillingScheduleId().toString());
        return line;
    }

    private void stubMilestonePlan(PaymentStructure structure, BillingPaymentEntry... entries) {
        BillingMilestonePlan plan = BillingMilestonePlan.builder()
                .milestonePlanId(UUID.randomUUID())
                .paymentStructure(structure)
                .isActive(true)
                .build();
        when(billingMilestonePlanRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(plan));
        when(billingPaymentEntryRepository.findByMilestonePlanAndIsActiveTrueOrderBySequenceAsc(plan))
                .thenReturn(List.of(entries));
    }

    private BillingPaymentEntry entry(int sequence, String amount, String remarks) {
        return BillingPaymentEntry.builder()
                .paymentEntryId(UUID.randomUUID())
                .sequence(sequence)
                .amount(new BigDecimal(amount))
                .remarks(remarks)
                .isActive(true)
                .build();
    }

    // ---------------------------------------------------------------- Fixed Price

    @Test
    void fixedPrice_oneTime_lineNamesTheProject() {
        BillingSchedule schedule = occurrence(1, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), "500000.00");

        InvoiceItemResponseDto line = generateSingleLine(schedule, configuration("Fixed Price", "One-Time"));

        assertThat(line.getItemType()).isEqualTo(BillingItemType.FIXED_PRICE);
        assertThat(line.getItemName()).isEqualTo("Fixed Price — Website Redesign");
    }

    @Test
    void fixedPrice_invoiceTotalsComeFromTheOccurrenceAndItsTaxCalculation() {
        BillingSchedule schedule = occurrence(1, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), "1000000.00");
        TaxCalculation tax = tax(schedule);
        tax.setTotalTaxAmount(new BigDecimal("180000.00"));
        tax.setGrandTotal(new BigDecimal("1180000.00"));
        for (String code : List.of("CGST", "SGST")) {
            tax.getComponents().add(TaxCalculationComponent.builder()
                    .taxCalculation(tax)
                    .taxTypeId(UUID.randomUUID())
                    .taxTypeCode(code)
                    .taxTypeName(code)
                    .appliedRate(new BigDecimal("9.00"))
                    .taxAmount(new BigDecimal("90000.00"))
                    .build());
        }
        when(billingScheduleRepository.findByIdForUpdate(schedule.getBillingScheduleId()))
                .thenReturn(Optional.of(schedule));
        when(taxCalculationRepository.findByBillingScheduleId(schedule.getBillingScheduleId()))
                .thenReturn(Optional.of(tax));
        when(billingConfigurationService.getBillingConfiguration(any()))
                .thenReturn(configuration("Fixed Price", "One-Time"));

        InvoiceResponseDto invoice = invoiceService.generateInvoiceForSchedule(schedule.getBillingScheduleId());

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.GENERATED);
        assertThat(invoice.getCurrencyCode()).isEqualTo("INR");
        assertThat(invoice.getSubtotal()).isEqualByComparingTo("1000000.00");
        assertThat(invoice.getTotalTaxAmount()).isEqualByComparingTo("180000.00");
        assertThat(invoice.getGrandTotal()).isEqualByComparingTo("1180000.00");
        assertThat(invoice.getItems()).singleElement().satisfies(line -> {
            assertThat(line.getItemType()).isEqualTo(BillingItemType.FIXED_PRICE);
            assertThat(line.getItemName()).isEqualTo("Fixed Price — Website Redesign");
            assertThat(line.getAmount()).isEqualByComparingTo("1000000.00");
        });
        assertThat(invoice.getTaxComponents()).hasSize(2);
        assertThat(schedule.getPeriodStatus()).isEqualTo(BillingPeriodStatus.INVOICED);
        assertThat(schedule.getIsInvoiced()).isTrue();
    }

    @Test
    void fixedPrice_billedInPeriods_lineAlsoNamesThePeriod() {
        BillingSchedule schedule = occurrence(2, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "100000.00");

        InvoiceItemResponseDto line = generateSingleLine(schedule, configuration("Fixed Price", "Monthly"));

        assertThat(line.getItemType()).isEqualTo(BillingItemType.FIXED_PRICE);
        assertThat(line.getItemName()).isEqualTo("Fixed Price — Website Redesign — Period 2");
    }

    @Test
    void unknownBillingType_keepsPreviousGenericFixedPriceLine() {
        BillingSchedule schedule = occurrence(3, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "100.00");

        InvoiceItemResponseDto line = generateSingleLine(schedule, configuration("Hybrid", null));

        assertThat(line.getItemType()).isEqualTo(BillingItemType.FIXED_PRICE);
        assertThat(line.getItemName()).isEqualTo("Hybrid - Period 3");
    }

    // ---------------------------------------------------------------- Milestone / Installment

    @Test
    void milestone_oneTimeFullPayment_isAMilestoneLine() {
        stubMilestonePlan(PaymentStructure.FULL_PAYMENT, entry(1, "500000.00", null));
        BillingSchedule schedule = occurrence(1, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 20), "500000.00");

        InvoiceItemResponseDto line = generateSingleLine(schedule, configuration("Milestone Plan", null));

        assertThat(line.getItemType()).isEqualTo(BillingItemType.MILESTONE);
        assertThat(line.getItemName()).isEqualTo("Milestone 1 — Payment");
    }

    @Test
    void milestone_fullPaymentWithRemarks_usesThem() {
        stubMilestonePlan(PaymentStructure.FULL_PAYMENT, entry(1, "500000.00", "Development Milestone"));
        BillingSchedule schedule = occurrence(1, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 20), "500000.00");

        InvoiceItemResponseDto line = generateSingleLine(schedule, configuration("Milestone Plan", null));

        assertThat(line.getItemName()).isEqualTo("Milestone 1 — Development Milestone");
    }

    @Test
    void installment_isNumberedOutOfTheTotalAndTypedMilestone() {
        stubMilestonePlan(PaymentStructure.INSTALLMENTS,
                entry(1, "400000.00", null),
                entry(2, "400000.00", "UAT sign-off"),
                entry(3, "200000.00", null));
        BillingSchedule schedule = occurrence(2, LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 15), "400000.00");

        InvoiceItemResponseDto line = generateSingleLine(schedule, configuration("Milestone Plan", null));

        assertThat(line.getItemType()).isEqualTo(BillingItemType.MILESTONE);
        assertThat(line.getItemName()).isEqualTo("Installment 2 of 3 — UAT sign-off");
        assertThat(line.getAmount()).isEqualByComparingTo("400000.00");
    }

    @Test
    void multipleMilestones_eachOccurrenceInvoicesItsOwnPaymentEntry() {
        BillingPaymentEntry first = entry(1, "300000.00", "Design");
        BillingPaymentEntry second = entry(2, "300000.00", "Build");
        BillingPaymentEntry third = entry(3, "400000.00", "Go-live");
        BillingMilestonePlan plan = BillingMilestonePlan.builder()
                .milestonePlanId(UUID.randomUUID())
                .paymentStructure(PaymentStructure.INSTALLMENTS)
                .isActive(true)
                .build();
        when(billingMilestonePlanRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(plan));
        when(billingPaymentEntryRepository.findByMilestonePlanAndIsActiveTrueOrderBySequenceAsc(plan))
                .thenReturn(List.of(first, second, third));
        when(billingConfigurationService.getBillingConfiguration(any()))
                .thenReturn(configuration("Milestone Plan", null));

        List<String> names = new ArrayList<>();
        for (BillingPaymentEntry paymentEntry : List.of(first, second, third)) {
            LocalDate date = LocalDate.of(2026, 10, paymentEntry.getSequence() * 5);
            BillingSchedule schedule = occurrence(paymentEntry.getSequence(), date, date,
                    paymentEntry.getAmount().toPlainString());
            when(billingScheduleRepository.findByIdForUpdate(schedule.getBillingScheduleId()))
                    .thenReturn(Optional.of(schedule));
            when(taxCalculationRepository.findByBillingScheduleId(schedule.getBillingScheduleId()))
                    .thenReturn(Optional.of(tax(schedule)));

            names.add(invoiceService.generateInvoiceForSchedule(schedule.getBillingScheduleId())
                    .getItems().get(0).getItemName());
        }

        assertThat(names).containsExactly(
                "Installment 1 of 3 — Design",
                "Installment 2 of 3 — Build",
                "Installment 3 of 3 — Go-live");
    }

    // ---------------------------------------------------------------- Recurring

    private BillingSchedule recurringOccurrence(LocalDate start, LocalDate end) {
        BillingSchedule schedule = occurrence(1, start, end, "200000.00");
        schedule.setRecurringConfiguration(BillingRecurringConfiguration.builder()
                .recurringConfigurationId(UUID.randomUUID())
                .recurringName("Monthly Retainer")
                .build());
        return schedule;
    }

    @Test
    void recurring_calendarMonthPeriod_isARetainerLineNamedByMonth() {
        BillingSchedule schedule = recurringOccurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        InvoiceItemResponseDto line = generateSingleLine(schedule, configuration("Recurring", "Monthly"));

        assertThat(line.getItemType()).isEqualTo(BillingItemType.RETAINER);
        assertThat(line.getItemName()).isEqualTo("Monthly Retainer — October 2026");
    }

    @Test
    void recurring_midMonthPeriod_isNamedByItsOwnDatesNotACalendarMonth() {
        BillingSchedule schedule = recurringOccurrence(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 14));

        InvoiceItemResponseDto line = generateSingleLine(schedule, configuration("Subscription", "Monthly"));

        assertThat(line.getItemType()).isEqualTo(BillingItemType.RETAINER);
        assertThat(line.getItemName()).isEqualTo("Monthly Retainer — 15 Oct 2026 – 14 Nov 2026");
    }

    // ---------------------------------------------------------------- Idempotency

    @Test
    void findOrGenerateInvoiceForSchedule_existingInvoice_isReturnedWithoutCreatingAnother() {
        BillingSchedule schedule = occurrence(1, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "1000.00");
        Invoice existing = Invoice.builder()
                .invoiceId(UUID.randomUUID())
                .invoiceNumber("INV-1")
                .billingScheduleId(schedule.getBillingScheduleId())
                .status(InvoiceStatus.PENDING_APPROVAL)
                .items(new ArrayList<>())
                .taxComponents(new ArrayList<>())
                .build();
        when(invoiceRepository.findByBillingScheduleId(schedule.getBillingScheduleId()))
                .thenReturn(Optional.of(existing));

        InvoiceResponseDto first = invoiceService.findOrGenerateInvoiceForSchedule(schedule.getBillingScheduleId());
        InvoiceResponseDto second = invoiceService.findOrGenerateInvoiceForSchedule(schedule.getBillingScheduleId());

        assertThat(first.getInvoiceId()).isEqualTo(existing.getInvoiceId());
        assertThat(second.getInvoiceId()).isEqualTo(existing.getInvoiceId());
        // Reuse never changes the approval lifecycle.
        assertThat(second.getStatus()).isEqualTo(InvoiceStatus.PENDING_APPROVAL);
        verify(invoiceRepository, never()).save(any());
        verifyNoInteractions(billingScheduleRepository, taxCalculationRepository);
    }

    @Test
    void findOrGenerateInvoiceForSchedule_noInvoiceYet_generatesItOnce() {
        BillingSchedule schedule = occurrence(1, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "1000.00");
        when(invoiceRepository.findByBillingScheduleId(schedule.getBillingScheduleId())).thenReturn(Optional.empty());
        when(billingScheduleRepository.findByIdForUpdate(schedule.getBillingScheduleId()))
                .thenReturn(Optional.of(schedule));
        when(taxCalculationRepository.findByBillingScheduleId(schedule.getBillingScheduleId()))
                .thenReturn(Optional.of(tax(schedule)));
        when(billingConfigurationService.getBillingConfiguration(any()))
                .thenReturn(configuration("Fixed Price", "Monthly"));

        InvoiceResponseDto response = invoiceService.findOrGenerateInvoiceForSchedule(schedule.getBillingScheduleId());

        assertThat(response.getStatus()).isEqualTo(InvoiceStatus.GENERATED);
        verify(invoiceRepository).save(any(Invoice.class));
        assertThat(schedule.getIsInvoiced()).isTrue();
    }

    @Test
    void findOrGenerateInvoice_snapshotAlreadyInvoiced_returnsExistingInvoice() {
        UUID snapshotId = UUID.randomUUID();
        Invoice existing = Invoice.builder()
                .invoiceId(UUID.randomUUID())
                .invoiceNumber("INV-2")
                .billingSnapshotId(snapshotId)
                .status(InvoiceStatus.APPROVED)
                .items(new ArrayList<>())
                .taxComponents(new ArrayList<>())
                .build();
        when(invoiceRepository.findByBillingSnapshotId(snapshotId)).thenReturn(Optional.of(existing));

        InvoiceResponseDto response = invoiceService.findOrGenerateInvoice(snapshotId);

        assertThat(response.getInvoiceId()).isEqualTo(existing.getInvoiceId());
        assertThat(response.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
        verify(invoiceRepository, never()).save(any());
        verifyNoInteractions(billingSnapshotRepository, taxCalculationRepository);
    }

    // ---------------------------------------------------------------- Failure

    @Test
    void invoiceInsertFails_occurrenceIsNotMarkedInvoiced() {
        BillingSchedule schedule = occurrence(1, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "1000.00");
        when(billingScheduleRepository.findByIdForUpdate(schedule.getBillingScheduleId()))
                .thenReturn(Optional.of(schedule));
        when(taxCalculationRepository.findByBillingScheduleId(schedule.getBillingScheduleId()))
                .thenReturn(Optional.of(tax(schedule)));
        when(billingConfigurationService.getBillingConfiguration(any()))
                .thenReturn(configuration("Fixed Price", "Monthly"));
        doThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_invoice_billing_schedule'"))
                .when(invoiceRepository).flush();

        assertThatThrownBy(() -> invoiceService.generateInvoiceForSchedule(schedule.getBillingScheduleId()))
                .isInstanceOf(GlobalExceptionHandler.DuplicateResourceException.class);

        assertThat(schedule.getIsInvoiced()).isFalse();
        assertThat(schedule.getPeriodStatus()).isEqualTo(BillingPeriodStatus.TAX_CALCULATED);
        verify(billingScheduleRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- invoice numbers (Phase 12 regression)

    @Test
    void invoicesGeneratedWithinTheSameSecond_getDistinctNumbersThatFitTheColumn() {
        java.util.Set<String> numbers = new java.util.HashSet<>();
        for (int i = 0; i < 500; i++) {
            String number = invoiceService.generateInvoiceNumber();
            assertThat(number).matches("INV-[0-9]{14}-[0-9A-F]{6}").hasSizeLessThanOrEqualTo(30);
            numbers.add(number);
        }
        assertThat(numbers).hasSize(500);
    }
}
