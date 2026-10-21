package com.AccountReceivableManagement.endtoend;

import com.AccountReceivableManagement.controller.invoice_generation.InvoiceApprovalController;
import com.AccountReceivableManagement.dto.UserDTO;
import com.AccountReceivableManagement.dto.billing_data_acquisition.TimesheetDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceDeliveryResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceRejectionRequestDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.entity.automatic_billing.AutomaticBillingRun;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.company_profile.CompanyProfile;
import com.AccountReceivableManagement.entity.invoice_generation.Invoice;
import com.AccountReceivableManagement.entity.invoice_generation.InvoiceItem;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFixedPriceConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFrequencyMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingMilestonePlan;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingPaymentEntry;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingRecurringConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTMRateCard;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.CurrencyMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.PaymentTermsMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxConfigurationComponent;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRegionMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxTypeMaster;
import com.AccountReceivableManagement.entity.receivable.Receivable;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.entity_enums.common.LockActionType;
import com.AccountReceivableManagement.entity_enums.common.LockResourceType;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingRatePeriod;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ContractValueSource;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.PaymentStructure;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.PricingModel;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationUnit;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxApplicabilityType;
import com.AccountReceivableManagement.integration.billing_data_acquisition.TimesheetIntegration;
import com.AccountReceivableManagement.repo.automatic_billing.AutomaticBillingRunRepository;
import com.AccountReceivableManagement.repo.client.ClientRepository;
import com.AccountReceivableManagement.repo.company_profile.CompanyProfileRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceDocumentRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingFixedPriceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingFrequencyMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingMilestonePlanRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingPaymentEntryRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingRecurringConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTMRateCardRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTypeMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.CurrencyMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.PaymentTermsMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRegionMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxTypeMasterRepository;
import com.AccountReceivableManagement.repo.receivable.ReceivableRepository;
import com.AccountReceivableManagement.repo.tax_calculation.TaxCalculationRepository;
import com.AccountReceivableManagement.security.CurrentUserService;
import com.AccountReceivableManagement.service_Imple.automatic_billing.AutomaticBillingScheduler;
import com.AccountReceivableManagement.service_Imple.projectbilling_config.BillingOccurrenceServiceImpl;
import com.AccountReceivableManagement.service_interface.concurrency_approval.RecordActionLockService;
import com.AccountReceivableManagement.service_interface.email.EmailService;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceDeliveryService;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end verification of the automatic billing lifecycle with the real
 * application context and services - occurrence generation, scheduler,
 * orchestrator, billing checks, tax engine, invoice generation, approval with
 * record locks, document finalization, manual delivery and receivable
 * creation - on an in-memory database. Only the edges outside AR are
 * replaced: TMS ({@link TimesheetIntegration}), the mail server
 * ({@link EmailService}) and the authenticated user ({@link CurrentUserService}).
 * <p>
 * Dates are relative to today (the scheduler uses the system clock), so the
 * test is independent of when it runs.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:automatic_billing_e2e;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "client-cdc.enabled=false",
        "project-cdc.enabled=false",
        "ar.security.authorization.configuration-approval-authorities=ROLE_FINANCE_MANAGER",
        "ar.security.authorization.invoice-approval-authorities=ROLE_FINANCE_MANAGER",
        "ar.security.authorization.invoice-delivery-authorities=ROLE_FINANCE_MANAGER",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/test-issuer",
        // Enabled, but only ticked by the test itself (the cron never fires during a run).
        "ar.automatic-billing.enabled=true",
        "ar.automatic-billing.cron=0 0 0 29 2 *"
})
@ActiveProfiles("test")
class AutomaticBillingEndToEndTest {

    private static final BigDecimal GST_HALF = new BigDecimal("9.00");

    @MockBean private TimesheetIntegration timesheetIntegration;
    @MockBean private EmailService emailService;
    @MockBean private CurrentUserService currentUserService;

    @Autowired private AutomaticBillingScheduler scheduler;
    @Autowired private BillingOccurrenceServiceImpl occurrenceService;
    @Autowired private InvoiceApprovalController approvalController;
    @Autowired private InvoiceService invoiceService;
    @Autowired private com.AccountReceivableManagement.service_interface.tax_calculation.TaxCalculationService taxService;
    @Autowired private InvoiceDeliveryService deliveryService;
    @Autowired private RecordActionLockService lockService;

    @Autowired private ClientRepository clientRepository;
    @Autowired private ProjectMasterReferenceRepository projectRepository;
    @Autowired private CurrencyMasterRepository currencyRepository;
    @Autowired private TaxRegionMasterRepository taxRegionRepository;
    @Autowired private TaxTypeMasterRepository taxTypeRepository;
    @Autowired private TaxConfigurationRepository taxConfigurationRepository;
    @Autowired private PaymentTermsMasterRepository paymentTermsRepository;
    @Autowired private BillingFrequencyMasterRepository frequencyRepository;
    @Autowired private BillingTypeMasterRepository billingTypeRepository;
    @Autowired private CompanyProfileRepository companyProfileRepository;
    @Autowired private BillingConfigurationRepository configurationRepository;
    @Autowired private BillingFixedPriceRepository fixedPriceRepository;
    @Autowired private BillingMilestonePlanRepository milestonePlanRepository;
    @Autowired private BillingPaymentEntryRepository paymentEntryRepository;
    @Autowired private BillingRecurringConfigurationRepository recurringRepository;
    @Autowired private BillingTMRateCardRepository rateCardRepository;
    @Autowired private BillingScheduleRepository scheduleRepository;
    @Autowired private AutomaticBillingRunRepository runRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private InvoiceDocumentRepository documentRepository;
    @Autowired private TaxCalculationRepository taxCalculationRepository;
    @Autowired private ReceivableRepository receivableRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private final LocalDate today = LocalDate.now();

    private Client client;
    private ProjectMasterReference project;
    private CurrencyMaster inr;
    private TaxRegionMaster karnataka;
    private PaymentTermsMaster net30;
    private BillingFrequencyMaster monthly;
    private BillingFrequencyMaster oneTime;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        when(currentUserService.getCurrentUser()).thenReturn(new UserDTO(
                7L, "finance.manager@example.com", "Finance Manager", List.of("FINANCE_MANAGER"), List.of()));
        when(currentUserService.getUserId()).thenReturn(7L);
        when(emailService.sendEmailWithAttachment(anyString(), anyString(), anyString(), any(byte[].class),
                anyString(), anyString())).thenReturn("<sent@test>");
        tx.executeWithoutResult(status -> seedMasterData());
    }

    // ================================================================ master data

    private void seedMasterData() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        inr = currencyRepository.findByCurrencyCodeIgnoreCase("INR").orElseGet(() -> currencyRepository.save(
                CurrencyMaster.builder().currencyCode("INR").currencyName("Indian Rupee").currencySymbol("₹")
                        .isActive(true).build()));
        karnataka = taxRegionRepository.findByTaxRegionNameIgnoreCase("Karnataka").orElseGet(() -> {
            TaxRegionMaster region = taxRegionRepository.save(TaxRegionMaster.builder()
                    .taxRegionCode("KA").taxRegionName("Karnataka").currencyCode("INR").isActive(true).build());
            TaxConfiguration gst = TaxConfiguration.builder()
                    .taxRegion(region)
                    .effectiveFrom(LocalDate.of(2020, 1, 1))
                    .isActive(true)
                    .components(new ArrayList<>())
                    .build();
            for (String[] component : new String[][] {{"CGST", "9.00", "SAME_JURISDICTION"},
                    {"SGST", "9.00", "SAME_JURISDICTION"}, {"IGST", "18.00", "DIFFERENT_JURISDICTION"}}) {
                TaxTypeMaster type = taxTypeRepository.save(TaxTypeMaster.builder()
                        .taxTypeCode(component[0]).taxTypeName(component[0]).isActive(true).build());
                gst.getComponents().add(TaxConfigurationComponent.builder()
                        .taxConfiguration(gst)
                        .taxType(type)
                        .taxRate(new BigDecimal(component[1]))
                        .applicabilityType(TaxApplicabilityType.valueOf(component[2]))
                        .isActive(true)
                        .build());
            }
            taxConfigurationRepository.save(gst);
            return region;
        });
        net30 = paymentTermsRepository.save(PaymentTermsMaster.builder()
                .paymentTermName("Net 30 " + suffix).paymentDays(30).isActive(true).build());
        monthly = frequency("Monthly", RenewalDurationUnit.MONTHS, 1);
        oneTime = frequency("One-Time", RenewalDurationUnit.MONTHS, 1);
        if (companyProfileRepository.findAll().stream().noneMatch(p -> Boolean.TRUE.equals(p.getIsActive()))) {
            companyProfileRepository.save(CompanyProfile.builder()
                    .legalName("Paves Technologies Pvt Ltd").state("Karnataka").country("India")
                    .gstin("29ABCDE1234F1Z5").email("billing@paves.example").isActive(true).build());
        }
        client = clientRepository.save(Client.builder()
                .clientId(UUID.randomUUID()).clientName("Acme Retail " + suffix)
                .email("ap+" + suffix + "@acme.example").build());
        project = projectRepository.save(ProjectMasterReference.builder()
                .pmsProjectId(Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L))
                .clientId(client.getClientId())
                .projectName("Store Modernisation " + suffix)
                .primaryLocation("Karnataka")
                .build());
    }

    private BillingFrequencyMaster frequency(String name, RenewalDurationUnit unit, int value) {
        return frequencyRepository.findByIsActiveTrueOrderByBillingFrequencyNameAsc().stream()
                .filter(f -> f.getBillingFrequencyName().equals(name)).findFirst()
                .orElseGet(() -> frequencyRepository.save(BillingFrequencyMaster.builder()
                        .billingFrequencyName(name).durationUnit(unit).durationValue(value).isActive(true).build()));
    }

    private BillingConfiguration configuration(String billingTypeName, BillingFrequencyMaster frequency,
            LocalDate effectiveFrom, LocalDate effectiveTo, PricingModel pricingModel) {
        return tx.execute(status -> {
            BillingTypeMaster type = billingTypeRepository.findAll().stream()
                    .filter(t -> t.getBillingTypeName().equals(billingTypeName)).findFirst()
                    .orElseGet(() -> billingTypeRepository.save(BillingTypeMaster.builder()
                            .billingTypeName(billingTypeName).isActive(true).build()));
            return configurationRepository.save(BillingConfiguration.builder()
                    .client(client)
                    .project(project)
                    .billingType(type)
                    .billingFrequency(frequency)
                    .currency(inr)
                    .taxRegion(karnataka)
                    .paymentTerm(net30)
                    .pricingModel(pricingModel)
                    .effectiveFrom(effectiveFrom)
                    .effectiveTo(effectiveTo)
                    // As configuration approval leaves it.
                    .approvalStatus(ApprovalStatus.APPROVED)
                    .billingStatus(com.AccountReceivableManagement.entity_enums.projectbilling_config
                            .BillingConfigurationStatus.ACTIVE)
                    .invoiceGenerationType(InvoiceGenerationType.AUTOMATIC)
                    .manuallyDeactivated(false)
                    .build());
        });
    }

    // ================================================================ helpers

    private List<BillingSchedule> occurrencesOf(BillingConfiguration configuration) {
        return scheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(configuration);
    }

    private List<Invoice> invoicesOf(List<BillingSchedule> occurrences) {
        List<UUID> ids = occurrences.stream().map(BillingSchedule::getBillingScheduleId).toList();
        return invoiceRepository.findAll().stream()
                .filter(invoice -> invoice.getBillingScheduleId() != null && ids.contains(invoice.getBillingScheduleId()))
                .sorted(Comparator.comparing(Invoice::getBillingPeriodStart))
                .toList();
    }

    private List<Invoice> invoicesOfProject() {
        return invoiceRepository.findAll().stream()
                .filter(invoice -> project.getPmsProjectId().equals(invoice.getProjectId()))
                .sorted(Comparator.comparing(Invoice::getBillingPeriodStart))
                .toList();
    }

    private List<InvoiceItem> itemsOf(Invoice invoice) {
        return tx.execute(status -> {
            List<InvoiceItem> items = invoiceRepository.findById(invoice.getInvoiceId()).orElseThrow().getItems();
            items.size();
            return new ArrayList<>(items);
        });
    }

    private InvoiceResponseDto approveAsFinanceManager(UUID invoiceId) {
        lockService.acquireLock(LockResourceType.INVOICE, invoiceId, LockActionType.APPROVAL);
        return approvalController.approve(invoiceId).getBody().getData();
    }

    /** Subtotal, tax and grand total of a same-state (CGST + SGST) GST invoice reconcile. */
    private void assertReconciles(Invoice invoice, String expectedSubtotal) {
        BigDecimal subtotal = new BigDecimal(expectedSubtotal);
        BigDecimal halfTax = subtotal.multiply(GST_HALF).divide(new BigDecimal("100"), 2,
                java.math.RoundingMode.HALF_UP);
        assertThat(invoice.getCurrencyCode()).isEqualTo("INR");
        assertThat(invoice.getSubtotal()).isEqualByComparingTo(subtotal);
        assertThat(invoice.getTotalTaxAmount()).isEqualByComparingTo(halfTax.add(halfTax));
        assertThat(invoice.getGrandTotal()).isEqualByComparingTo(subtotal.add(halfTax).add(halfTax));
        assertThat(itemsOf(invoice).stream().map(InvoiceItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(subtotal);
    }

    // ================================================================ Fixed Price - the complete lifecycle

    @Test
    void fixedPrice_fromConfigurationToReceivable() {
        LocalDate from = today.minusMonths(3).withDayOfMonth(1);
        LocalDate to = from.plusMonths(6).minusDays(1);
        BillingConfiguration configuration = configuration("Fixed Price", monthly, from, to, null);
        tx.executeWithoutResult(status -> fixedPriceRepository.save(BillingFixedPriceConfiguration.builder()
                .billingConfiguration(configuration).contractValue(new BigDecimal("600000.00"))
                .contractValueSource(ContractValueSource.MANUAL).isActive(true).build()));

        // 1. Occurrence generation: six monthly payments of 100000, never the whole contract.
        // As configuration approval does, inside its transaction.
        tx.executeWithoutResult(status ->
                occurrenceService.generateOccurrencesForFixedPrice(configuration.getBillingConfigurationId()));
        List<BillingSchedule> occurrences = occurrencesOf(configuration);
        assertThat(occurrences).hasSize(6)
                .allSatisfy(o -> assertThat(o.getBillingAmount()).isEqualByComparingTo("100000.00"));
        List<BillingSchedule> due = occurrences.stream().filter(o -> !o.getBillingDate().isAfter(today)).toList();
        assertThat(due).hasSizeGreaterThanOrEqualTo(3);

        // 2. Scheduler: one run, tax calculation and invoice per due occurrence - none early.
        scheduler.runOnce();
        List<Invoice> invoices = invoicesOf(occurrences);
        assertThat(invoices).hasSize(due.size())
                .allSatisfy(invoice -> assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING_APPROVAL));
        for (Invoice invoice : invoices) {
            assertReconciles(invoice, "100000.00");
            assertThat(itemsOf(invoice)).singleElement().satisfies(line -> {
                assertThat(line.getItemType()).isEqualTo(BillingItemType.FIXED_PRICE);
                assertThat(line.getItemName()).startsWith("Fixed Price — Store Modernisation");
            });
            assertThat(runRepository.findByBillingScheduleId(invoice.getBillingScheduleId()).orElseThrow()
                    .getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        }

        // 3. Repeated tick: nothing duplicated.
        long taxCalculations = taxCalculationRepository.count();
        long invoiceCount = invoiceRepository.count();
        AutomaticBillingScheduler.TickSummary repeat = scheduler.runOnce();
        assertThat(invoiceRepository.count()).isEqualTo(invoiceCount);
        assertThat(taxCalculationRepository.count()).isEqualTo(taxCalculations);
        assertThat(invoicesOf(occurrences)).hasSize(due.size());

        // Nothing was approved, sent or turned into a receivable by automation.
        verify(emailService, never()).sendEmailWithAttachment(any(), any(), any(), any(), any(), any());
        assertThat(invoices).allSatisfy(invoice ->
                assertThat(receivableRepository.findByInvoiceId(invoice.getInvoiceId())).isEmpty());

        // 4. Manual approval (with the record lock) -> document finalized -> READY_TO_SEND.
        Invoice first = invoices.get(0);
        InvoiceResponseDto approved = approveAsFinanceManager(first.getInvoiceId());
        assertThat(approved.getStatus()).isEqualTo(InvoiceStatus.READY_TO_SEND);
        assertThat(approved.isDocumentAvailable()).isTrue();
        assertThat(documentRepository.existsByInvoiceId(first.getInvoiceId())).isTrue();
        assertThat(runRepository.findByBillingScheduleId(first.getBillingScheduleId()).orElseThrow().getStatus())
                .isEqualTo(AutomaticBillingRunStatus.COMPLETED);
        // READY_TO_SEND is not sent: no receivable yet.
        assertThat(receivableRepository.findByInvoiceId(first.getInvoiceId())).isEmpty();

        // 5. Manual rejection of another: never finalized, never sendable.
        Invoice second = invoices.get(1);
        lockService.acquireLock(LockResourceType.INVOICE, second.getInvoiceId(), LockActionType.APPROVAL);
        InvoiceRejectionRequestDto rejection = new InvoiceRejectionRequestDto();
        rejection.setReason("Incorrect purchase order reference.");
        invoiceService.rejectInvoice(second.getInvoiceId(), rejection);
        assertThatThrownBy(() -> invoiceService.finalizeInvoiceDocument(second.getInvoiceId()))
                .hasMessageContaining("Only an approved invoice");
        assertThatThrownBy(() -> deliveryService.sendInvoice(second.getInvoiceId()))
                .hasMessageContaining("must be approved");

        // 6. Manual delivery -> SENT -> exactly one receivable for the grand total.
        InvoiceDeliveryResponseDto sent = deliveryService.sendInvoice(first.getInvoiceId());
        assertThat(sent.getDeliveryStatus()).isEqualTo(InvoiceDeliveryStatus.SENT);
        Receivable receivable = receivableRepository.findByInvoiceId(first.getInvoiceId()).orElseThrow();
        assertThat(receivable.getOutstandingBalance()).isEqualByComparingTo("118000.00");
        assertThat(receivable.getCurrencyCode()).isEqualTo("INR");

        // 7. Repeated delivery (G-19): refused - no second email, still one receivable.
        assertThatThrownBy(() -> deliveryService.sendInvoice(first.getInvoiceId()))
                .hasMessageContaining("already been sent");
        assertThat(receivableRepository.findAll().stream()
                .filter(r -> r.getInvoice().getInvoiceId().equals(first.getInvoiceId()))).hasSize(1);
        verify(emailService, times(1)).sendEmailWithAttachment(any(), any(), any(), any(), any(), any());
        assertThat(invoiceRepository.findById(first.getInvoiceId()).orElseThrow().getStatus())
                .isEqualTo(InvoiceStatus.READY_TO_SEND);
    }

    // ================================================================ Milestone Plan - installments

    @Test
    void installments_eachDueInstallmentIsInvoicedForItsOwnAmount() {
        BillingConfiguration configuration = configuration("Milestone Plan", oneTime,
                today.minusMonths(4), today.plusMonths(4), null);
        LocalDate first = today.minusMonths(2);
        LocalDate second = today.minusDays(5);
        LocalDate future = today.plusMonths(1);
        tx.executeWithoutResult(status -> {
            BillingMilestonePlan plan = milestonePlanRepository.save(BillingMilestonePlan.builder()
                    .billingConfiguration(configuration).totalContractValue(new BigDecimal("1000000.00"))
                    .paymentStructure(PaymentStructure.INSTALLMENTS).isActive(true).build());
            List<BillingPaymentEntry> entries = new ArrayList<>();
            Object[][] rows = {{1, "20.00", "200000.00", first, "Design sign-off"},
                    {2, "30.00", "300000.00", second, "Build complete"},
                    {3, "50.00", "500000.00", future, "Go-live"}};
            for (Object[] row : rows) {
                entries.add(paymentEntryRepository.save(BillingPaymentEntry.builder()
                        .milestonePlan(plan).sequence((Integer) row[0]).percentage(new BigDecimal((String) row[1]))
                        .amount(new BigDecimal((String) row[2])).billingDate((LocalDate) row[3])
                        .remarks((String) row[4]).isActive(true).build()));
            }
            occurrenceService.generateOccurrencesForMilestonePlan(
                    configurationRepository.findById(configuration.getBillingConfigurationId()).orElseThrow(),
                    plan, entries);
        });
        List<BillingSchedule> occurrences = occurrencesOf(configuration);
        assertThat(occurrences).hasSize(3);

        scheduler.runOnce();
        scheduler.runOnce();

        List<Invoice> invoices = invoicesOf(occurrences);
        assertThat(invoices).hasSize(2);
        assertReconciles(invoices.get(0), "200000.00");
        assertReconciles(invoices.get(1), "300000.00");
        assertThat(itemsOf(invoices.get(0))).singleElement().satisfies(line -> {
            assertThat(line.getItemType()).isEqualTo(BillingItemType.MILESTONE);
            assertThat(line.getItemName()).isEqualTo("Installment 1 of 3 — Design sign-off");
        });
        assertThat(itemsOf(invoices.get(1)).get(0).getItemName()).isEqualTo("Installment 2 of 3 — Build complete");
        assertThat(invoices).allSatisfy(i -> assertThat(i.getStatus()).isEqualTo(InvoiceStatus.PENDING_APPROVAL));
    }

    // ================================================================ Recurring / retainer

    @Test
    void recurringRetainer_eachDueMonthIsInvoicedForItsShare_andTheCurrentMonthIsNotBilledEarly() {
        LocalDate start = today.minusMonths(3).withDayOfMonth(1);
        LocalDate end = start.plusMonths(12).minusDays(1);
        BillingConfiguration configuration = configuration("Recurring", monthly, start, end, null);
        tx.executeWithoutResult(status -> {
            recurringRepository.save(BillingRecurringConfiguration.builder()
                    .billingConfiguration(configuration).recurringName("Managed Support Retainer")
                    .contractValue(new BigDecimal("1200000.00")).contractValueSource(ContractValueSource.MANUAL)
                    .billingFrequency(monthly).recurringStartDate(start).recurringEndDate(end)
                    .isActive(true).build());
            occurrenceService.generateOccurrencesForRecurring(configuration.getBillingConfigurationId());
        });
        List<BillingSchedule> occurrences = occurrencesOf(configuration);
        assertThat(occurrences).hasSize(12);
        List<BillingSchedule> due = occurrences.stream().filter(o -> !o.getBillingDate().isAfter(today)).toList();

        scheduler.runOnce();
        scheduler.runOnce();

        List<Invoice> invoices = invoicesOf(occurrences);
        assertThat(invoices).hasSize(due.size());
        for (int i = 0; i < invoices.size(); i++) {
            BillingSchedule occurrence = due.get(i);
            Invoice invoice = invoices.get(i);
            // Each invoice is its own period's share - never the contract value.
            assertReconciles(invoice, occurrence.getBillingAmount().toPlainString());
            assertThat(invoice.getSubtotal()).isLessThan(new BigDecimal("1200000.00"));
            assertThat(invoice.getBillingPeriodStart()).isEqualTo(occurrence.getPeriodStartDate());
            assertThat(invoice.getBillingPeriodEnd()).isEqualTo(occurrence.getPeriodEndDate());
            assertThat(itemsOf(invoice)).singleElement().satisfies(line -> {
                assertThat(line.getItemType()).isEqualTo(BillingItemType.RETAINER);
                assertThat(line.getItemName()).startsWith("Managed Support Retainer — ");
            });
        }
    }

    // ================================================================ Time & Material

    @Test
    void timeAndMaterial_billsEachRolesHoursAtItsRate_andNeverBillsATimesheetTwice() {
        LocalDate start = today.minusMonths(2).withDayOfMonth(1);
        BillingConfiguration configuration = configuration("Timesheet Based", monthly, start,
                start.plusMonths(12).minusDays(1), PricingModel.ROLE_BASED);
        tx.executeWithoutResult(status -> {
            for (Object[] card : new Object[][] {{"Developer", "1500.00"}, {"Senior Developer", "2000.00"}}) {
                rateCardRepository.save(BillingTMRateCard.builder().billingConfiguration(configuration)
                        .roleName((String) card[0]).rate(new BigDecimal((String) card[1]))
                        .ratePeriod(BillingRatePeriod.HOURLY).effectiveFrom(start).isActive(true).build());
            }
        });
        String batch = UUID.randomUUID().toString().substring(0, 8);
        when(timesheetIntegration.getApprovedTimesheets(any(), any(), any())).thenAnswer(invocation -> {
            LocalDate periodStart = invocation.getArgument(1);
            String key = batch + "-" + periodStart;
            return new ArrayList<>(List.of(
                    TimesheetDto.builder().sourceReferenceId("TS-" + key + "-1").resourceName("Asha Rao")
                            .role("Developer").workDate(periodStart.plusDays(2)).hours(new BigDecimal("8"))
                            .approvalStatus("APPROVED").approved(true).billable(true).build(),
                    TimesheetDto.builder().sourceReferenceId("TS-" + key + "-2").resourceName("Ravi Kumar")
                            .role("Senior Developer").workDate(periodStart.plusDays(3)).hours(new BigDecimal("5"))
                            .approvalStatus("APPROVED").approved(true).billable(true).build()));
        });

        scheduler.runOnce();
        scheduler.runOnce();

        List<Invoice> invoices = invoicesOfProject();
        assertThat(invoices).hasSizeGreaterThanOrEqualTo(1);
        for (Invoice invoice : invoices) {
            assertThat(invoice.getBillingSnapshotId()).isNotNull();
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING_APPROVAL);
            // Developer 8 x 1500 + Senior Developer 5 x 2000.
            assertReconciles(invoice, "22000.00");
            assertThat(itemsOf(invoice)).extracting(InvoiceItem::getItemType)
                    .containsOnly(BillingItemType.TIME_ENTRY);
            assertThat(itemsOf(invoice)).extracting(InvoiceItem::getAmount)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactlyInAnyOrder(new BigDecimal("12000.00"), new BigDecimal("10000.00"));
        }
        // One TMS call per period - the repeated tick reused everything.
        verify(timesheetIntegration, times(invoices.size())).getApprovedTimesheets(any(), any(), any());
        List<AutomaticBillingRun> runs = runRepository.findAll().stream()
                .filter(r -> r.getBillingConfiguration().getBillingConfigurationId()
                        .equals(configuration.getBillingConfigurationId())).toList();
        assertThat(runs).hasSize(invoices.size())
                .allSatisfy(run -> assertThat(run.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL));
    }

    // ================================================================ manual billing and expired configurations

    @Test
    void occurrenceAlreadyInvoicedManually_isNotInvoicedAgainByAutomation() {
        LocalDate from = today.minusMonths(2).withDayOfMonth(1);
        BillingConfiguration configuration = configuration("Fixed Price", monthly, from,
                from.plusMonths(2).minusDays(1), null);
        tx.executeWithoutResult(status -> {
            fixedPriceRepository.save(BillingFixedPriceConfiguration.builder()
                    .billingConfiguration(configuration).contractValue(new BigDecimal("200000.00"))
                    .contractValueSource(ContractValueSource.MANUAL).isActive(true).build());
            occurrenceService.generateOccurrencesForFixedPrice(configuration.getBillingConfigurationId());
        });
        List<BillingSchedule> occurrences = occurrencesOf(configuration);
        BillingSchedule manual = occurrences.get(0);
        // The manual flow: occurrence made TAX_PENDING, tax calculated, invoice generated.
        tx.executeWithoutResult(status -> {
            BillingSchedule occurrence = scheduleRepository.findById(manual.getBillingScheduleId()).orElseThrow();
            occurrence.setPeriodStatus(BillingPeriodStatus.TAX_PENDING);
            occurrence.setTaxStatus(BillingPeriodStatus.TAX_PENDING);
            scheduleRepository.save(occurrence);
        });
        taxService.calculateTaxForSchedule(manual.getBillingScheduleId());
        UUID manualInvoice = invoiceService.generateInvoiceForSchedule(manual.getBillingScheduleId()).getInvoiceId();

        scheduler.runOnce();

        assertThat(invoicesOf(occurrences)).extracting(Invoice::getBillingScheduleId)
                .containsExactlyInAnyOrder(occurrences.get(0).getBillingScheduleId(),
                        occurrences.get(1).getBillingScheduleId());
        assertThat(runRepository.findByBillingScheduleId(manual.getBillingScheduleId())).isEmpty();
        assertThat(invoiceRepository.findById(manualInvoice).orElseThrow().getStatus())
                .isEqualTo(InvoiceStatus.GENERATED);
    }

    @Test
    void expiredConfiguration_itsFinalDueOccurrenceIsStillBilled() {
        LocalDate from = today.minusMonths(3).withDayOfMonth(1);
        LocalDate to = today.minusMonths(1).withDayOfMonth(1).minusDays(1);
        BillingConfiguration configuration = configuration("Fixed Price", oneTime, from, to, null);
        tx.executeWithoutResult(status -> {
            fixedPriceRepository.save(BillingFixedPriceConfiguration.builder()
                    .billingConfiguration(configuration).contractValue(new BigDecimal("250000.00"))
                    .contractValueSource(ContractValueSource.MANUAL).isActive(true).build());
            occurrenceService.generateOccurrencesForFixedPrice(configuration.getBillingConfigurationId());
        });
        List<BillingSchedule> occurrences = occurrencesOf(configuration);
        assertThat(occurrences).singleElement().satisfies(o ->
                assertThat(o.getBillingAmount()).isEqualByComparingTo("250000.00"));

        scheduler.runOnce();

        assertThat(invoicesOf(occurrences)).singleElement().satisfies(invoice -> {
            assertReconciles(invoice, "250000.00");
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING_APPROVAL);
        });
    }

    // ================================================================ Phase 14 - Finance reconciliation (UAT scenarios)

    /** Prints one expected-vs-actual row - the UAT evidence - and asserts it. */
    private void reconcile(String scenario, String field, BigDecimal expected, BigDecimal actual) {
        System.out.printf("UAT-RECON | %-34s | %-22s | expected %14s | actual %14s | %s%n", scenario, field,
                expected.toPlainString(), actual == null ? "null" : actual.toPlainString(),
                actual != null && expected.compareTo(actual) == 0 ? "MATCH" : "DIFF");
        assertThat(actual).as(scenario + " - " + field).isEqualByComparingTo(expected);
    }

    private BigDecimal lineTotal(Invoice invoice) {
        return itemsOf(invoice).stream().map(InvoiceItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal taxOf(Invoice invoice, String code) {
        return tx.execute(status -> invoiceRepository.findById(invoice.getInvoiceId()).orElseThrow()
                .getTaxComponents().stream().filter(c -> code.equals(c.getTaxTypeCode()))
                .map(com.AccountReceivableManagement.entity.invoice_generation.InvoiceTaxComponent::getTaxAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void uat_timeAndMaterial_fractionalHoursAndRates_reconcileToThePaisa() {
        LocalDate start = today.minusMonths(1).withDayOfMonth(1);
        BillingConfiguration configuration = configuration("Timesheet Based", monthly, start,
                start.plusMonths(1).minusDays(1), PricingModel.ROLE_BASED);
        tx.executeWithoutResult(status -> {
            for (Object[] card : new Object[][] {{"Developer", "1500.50"}, {"Tester", "999.99"}}) {
                rateCardRepository.save(BillingTMRateCard.builder().billingConfiguration(configuration)
                        .roleName((String) card[0]).rate(new BigDecimal((String) card[1]))
                        .ratePeriod(BillingRatePeriod.HOURLY).effectiveFrom(start).isActive(true).build());
            }
        });
        String batch = UUID.randomUUID().toString().substring(0, 8);
        when(timesheetIntegration.getApprovedTimesheets(any(), any(), any())).thenAnswer(invocation -> {
            LocalDate periodStart = invocation.getArgument(1);
            List<TimesheetDto> timesheets = new ArrayList<>();
            Object[][] rows = {{"Asha Rao", "Developer", "7.25"}, {"Ravi Kumar", "Developer", "7.25"},
                    {"Meena Iyer", "Tester", "3.50"}};
            for (int i = 0; i < rows.length; i++) {
                timesheets.add(TimesheetDto.builder().sourceReferenceId("TS-" + batch + "-" + i)
                        .resourceName((String) rows[i][0]).role((String) rows[i][1])
                        .workDate(periodStart.plusDays(i + 1)).hours(new BigDecimal((String) rows[i][2]))
                        .approvalStatus("APPROVED").approved(true).billable(true).build());
            }
            return timesheets;
        });

        scheduler.runOnce();

        Invoice invoice = invoicesOfProject().get(0);
        // Hand calculation: 7.25 x 1500.50 = 10878.625 -> 10878.63 (twice); 3.50 x 999.99 = 3499.965 -> 3499.97.
        BigDecimal lines = new BigDecimal("10878.63").add(new BigDecimal("10878.63")).add(new BigDecimal("3499.97"));
        String scenario = "T&M fractional hours (KA->KA)";
        reconcile(scenario, "sum of line amounts", lines, lineTotal(invoice));
        reconcile(scenario, "invoice subtotal", lines, invoice.getSubtotal());
        BigDecimal half = lines.multiply(new BigDecimal("9")).divide(new BigDecimal("100"), 2,
                java.math.RoundingMode.HALF_UP);
        reconcile(scenario, "CGST 9%", half, taxOf(invoice, "CGST"));
        reconcile(scenario, "SGST 9%", half, taxOf(invoice, "SGST"));
        reconcile(scenario, "grand total", lines.add(half).add(half), invoice.getGrandTotal());
    }

    @Test
    void uat_interStateProject_isTaxedWithIgstOnly() {
        tx.executeWithoutResult(status -> {
            if (taxRegionRepository.findByTaxRegionNameIgnoreCase("Maharashtra").isEmpty()) {
                taxRegionRepository.save(TaxRegionMaster.builder().taxRegionCode("MH").taxRegionName("Maharashtra")
                        .currencyCode("INR").isActive(true).build());
            }
            project.setPrimaryLocation("Maharashtra");
            projectRepository.save(project);
        });
        LocalDate from = today.minusMonths(1).withDayOfMonth(1);
        BillingConfiguration configuration = configuration("Fixed Price", oneTime, from,
                from.plusMonths(1).minusDays(1), null);
        tx.executeWithoutResult(status -> {
            fixedPriceRepository.save(BillingFixedPriceConfiguration.builder()
                    .billingConfiguration(configuration).contractValue(new BigDecimal("123456.78"))
                    .contractValueSource(ContractValueSource.MANUAL).isActive(true).build());
            occurrenceService.generateOccurrencesForFixedPrice(configuration.getBillingConfigurationId());
        });

        scheduler.runOnce();

        Invoice invoice = invoicesOf(occurrencesOf(configuration)).get(0);
        String scenario = "Fixed Price inter-state (KA->MH)";
        BigDecimal igst = new BigDecimal("123456.78").multiply(new BigDecimal("18"))
                .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        reconcile(scenario, "invoice subtotal", new BigDecimal("123456.78"), invoice.getSubtotal());
        reconcile(scenario, "IGST 18%", igst, taxOf(invoice, "IGST"));
        reconcile(scenario, "CGST", BigDecimal.ZERO, taxOf(invoice, "CGST"));
        reconcile(scenario, "SGST", BigDecimal.ZERO, taxOf(invoice, "SGST"));
        reconcile(scenario, "grand total", new BigDecimal("123456.78").add(igst), invoice.getGrandTotal());
    }

    @Test
    void uat_fixedPriceThatDoesNotDivideEvenly_billsExactlyTheContractValueOverItsPayments() {
        LocalDate from = today.minusMonths(3).withDayOfMonth(1);
        BillingConfiguration configuration = configuration("Fixed Price", monthly, from,
                from.plusMonths(3).minusDays(1), null);
        tx.executeWithoutResult(status -> {
            fixedPriceRepository.save(BillingFixedPriceConfiguration.builder()
                    .billingConfiguration(configuration).contractValue(new BigDecimal("1000000.00"))
                    .contractValueSource(ContractValueSource.MANUAL).isActive(true).build());
            occurrenceService.generateOccurrencesForFixedPrice(configuration.getBillingConfigurationId());
        });

        scheduler.runOnce();

        List<Invoice> invoices = invoicesOf(occurrencesOf(configuration));
        String scenario = "Fixed Price 1,000,000 / 3";
        assertThat(invoices).hasSize(3);
        reconcile(scenario, "payment 1 subtotal", new BigDecimal("333333.33"), invoices.get(0).getSubtotal());
        reconcile(scenario, "payment 2 subtotal", new BigDecimal("333333.33"), invoices.get(1).getSubtotal());
        reconcile(scenario, "payment 3 subtotal", new BigDecimal("333333.34"), invoices.get(2).getSubtotal());
        reconcile(scenario, "sum of payments", new BigDecimal("1000000.00"), invoices.stream()
                .map(Invoice::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void uat_deliveredInvoice_receivableBalanceEqualsTheGrandTotal() {
        LocalDate from = today.minusMonths(1).withDayOfMonth(1);
        BillingConfiguration configuration = configuration("Fixed Price", oneTime, from,
                from.plusMonths(1).minusDays(1), null);
        tx.executeWithoutResult(status -> {
            fixedPriceRepository.save(BillingFixedPriceConfiguration.builder()
                    .billingConfiguration(configuration).contractValue(new BigDecimal("87654.32"))
                    .contractValueSource(ContractValueSource.MANUAL).isActive(true).build());
            occurrenceService.generateOccurrencesForFixedPrice(configuration.getBillingConfigurationId());
        });
        scheduler.runOnce();
        Invoice invoice = invoicesOf(occurrencesOf(configuration)).get(0);
        approveAsFinanceManager(invoice.getInvoiceId());
        deliveryService.sendInvoice(invoice.getInvoiceId());

        Receivable receivable = receivableRepository.findByInvoiceId(invoice.getInvoiceId()).orElseThrow();
        BigDecimal half = new BigDecimal("87654.32").multiply(new BigDecimal("9"))
                .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        String scenario = "Receivable after delivery";
        reconcile(scenario, "invoice grand total", new BigDecimal("87654.32").add(half).add(half),
                invoice.getGrandTotal());
        reconcile(scenario, "receivable amount", invoice.getGrandTotal(), receivable.getInvoiceAmount());
        reconcile(scenario, "outstanding balance", invoice.getGrandTotal(), receivable.getOutstandingBalance());
    }
}
