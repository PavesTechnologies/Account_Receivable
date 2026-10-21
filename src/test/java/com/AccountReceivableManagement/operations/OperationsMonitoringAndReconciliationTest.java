package com.AccountReceivableManagement.operations;

import com.AccountReceivableManagement.config.properties.OperationsMonitoringProperties;
import com.AccountReceivableManagement.controller.invoice_generation.InvoiceApprovalController;
import com.AccountReceivableManagement.dto.UserDTO;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceRejectionRequestDto;
import com.AccountReceivableManagement.dto.operations.BillingOperationsSummaryDto;
import com.AccountReceivableManagement.dto.operations.OperationsAlertDto;
import com.AccountReceivableManagement.dto.operations.ReconciliationReportDto;
import com.AccountReceivableManagement.dto.operations.ReconciliationReportDto.CheckResult;
import com.AccountReceivableManagement.entity.automatic_billing.AutomaticBillingRun;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.company_profile.CompanyProfile;
import com.AccountReceivableManagement.entity.invoice_generation.Invoice;
import com.AccountReceivableManagement.entity.invoice_generation.InvoiceDelivery;
import com.AccountReceivableManagement.entity.invoice_generation.InvoiceItem;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFixedPriceConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFrequencyMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.CurrencyMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.PaymentTermsMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxConfigurationComponent;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRegionMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxTypeMaster;
import com.AccountReceivableManagement.entity.receivable.Receivable;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingStep;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.entity_enums.common.LockActionType;
import com.AccountReceivableManagement.entity_enums.common.LockResourceType;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.entity_enums.operations.OperationsAlertCondition;
import com.AccountReceivableManagement.entity_enums.operations.ReconciliationCheck;
import com.AccountReceivableManagement.entity_enums.operations.ReconciliationCheck.Severity;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ContractValueSource;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationUnit;
import com.AccountReceivableManagement.entity_enums.receivable.ReceivableStatus;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxApplicabilityType;
import com.AccountReceivableManagement.integration.billing_data_acquisition.TimesheetIntegration;
import com.AccountReceivableManagement.repo.automatic_billing.AutomaticBillingRunRepository;
import com.AccountReceivableManagement.repo.client.ClientRepository;
import com.AccountReceivableManagement.repo.company_profile.CompanyProfileRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceDeliveryRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingFixedPriceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingFrequencyMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTypeMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.CurrencyMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.PaymentTermsMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRegionMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxTypeMasterRepository;
import com.AccountReceivableManagement.repo.receivable.ReceivableRepository;
import com.AccountReceivableManagement.security.CurrentUserService;
import com.AccountReceivableManagement.service_Imple.automatic_billing.AutomaticBillingScheduler;
import com.AccountReceivableManagement.service_Imple.operations.OperationsAlertJob;
import com.AccountReceivableManagement.service_Imple.projectbilling_config.BillingOccurrenceServiceImpl;
import com.AccountReceivableManagement.service_interface.concurrency_approval.RecordActionLockService;
import com.AccountReceivableManagement.service_interface.email.EmailService;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceDeliveryService;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import com.AccountReceivableManagement.service_interface.operations.OperationsReportService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Phase 15 - operations monitoring, reconciliation and alerting against the
 * real application context on an in-memory database (H2, MySQL mode). The
 * billing records are produced by the real pipeline (occurrence generation,
 * scheduler, tax, invoice, approval, document, delivery, receivable); only
 * TMS, the mail server and the logged-in user are simulated. Discrepancies
 * are introduced deliberately with SQL, as an out-of-band change in
 * production would. Development-environment evidence only - not a
 * production observation.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:operations_monitoring;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
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
        // Ticked by the test only.
        "ar.automatic-billing.enabled=true",
        "ar.automatic-billing.cron=0 0 0 29 2 *",
        "ar.operations.report-authorities=ROLE_AR_OPERATIONS",
        "ar.operations.alerts.cron=0 0 0 29 2 *"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperationsMonitoringAndReconciliationTest {

    /** Financial and execution tables whose content monitoring must never change. */
    private static final List<String> FINANCIAL_TABLES = List.of("automatic_billing_run", "billing_schedule",
            "tax_calculation", "invoice", "invoice_item", "invoice_tax_component", "invoice_document",
            "invoice_delivery", "receivable");

    @MockBean private TimesheetIntegration timesheetIntegration;
    @MockBean private EmailService emailService;
    @MockBean private CurrentUserService currentUserService;

    @Autowired private OperationsReportService reportService;
    @Autowired private OperationsAlertJob alertJob;
    @Autowired private OperationsMonitoringProperties properties;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Autowired private AutomaticBillingScheduler scheduler;
    @Autowired private BillingOccurrenceServiceImpl occurrenceService;
    @Autowired private InvoiceApprovalController approvalController;
    @Autowired private InvoiceService invoiceService;
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
    @Autowired private BillingScheduleRepository scheduleRepository;
    @Autowired private AutomaticBillingRunRepository runRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private InvoiceDeliveryRepository deliveryRepository;
    @Autowired private ReceivableRepository receivableRepository;

    private TransactionTemplate tx;
    private final LocalDate today = LocalDate.now();
    private Client client;
    private ProjectMasterReference project;
    private CurrencyMaster inr;
    private TaxRegionMaster karnataka;
    private PaymentTermsMaster net30;
    private BillingFrequencyMaster monthly;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        emptyDatabase();
        when(currentUserService.getCurrentUser()).thenReturn(new UserDTO(
                7L, "finance.manager@example.com", "Finance Manager", List.of("FINANCE_MANAGER"), List.of()));
        when(currentUserService.getUserId()).thenReturn(7L);
        when(emailService.sendEmailWithAttachment(anyString(), anyString(), anyString(), any(byte[].class),
                anyString(), anyString())).thenReturn("<sent@test>");
        tx.executeWithoutResult(status -> seedMasterData());
    }

    @AfterEach
    void restoreProperties() {
        properties.getAlerts().getThresholds().clear();
        properties.setReportAuthorities(List.of("ROLE_AR_OPERATIONS"));
    }

    // ================================================================ monitoring reflects persisted status

    @Test
    void realPipeline_reconcilesWithoutFindings_andSummaryMatchesPersistedRecords() {
        Pipeline pipeline = billFixedPriceAndProcessInvoices();

        ReconciliationReportDto report = reportService.reconcile();
        assertThat(report.checksExecuted()).isEqualTo(ReconciliationCheck.values().length);
        assertThat(report.checks()).allSatisfy(check ->
                assertThat(check.findingCount()).as(check.check().name()).isZero());
        assertThat(report.totalFindings()).isZero();

        BillingOperationsSummaryDto summary = reportService.getSummary(30);

        // Execution status = the persisted runs, exactly.
        Map<String, Long> runsByStatus = runRepository.findAll().stream()
                .collect(Collectors.groupingBy(run -> run.getStatus().name(), Collectors.counting()));
        assertThat(summary.automaticBillingRuns().byStatus()).isEqualTo(runsByStatus);
        assertThat(summary.automaticBillingRuns().total()).isEqualTo(pipeline.invoices.size());
        assertThat(summary.automaticBillingRuns().byBillingTypeAndStatus())
                .allSatisfy(row -> assertThat(row.billingType()).isEqualTo("Fixed Price"));
        assertThat(summary.automaticBillingRuns().byCreatedDateAndStatus())
                .allSatisfy(row -> assertThat(row.date()).isEqualTo(today));
        assertThat(summary.automaticBillingRuns().needingIntervention()).isZero();
        assertThat(summary.automaticBillingRuns().stuck()).isZero();

        // Business status = the persisted invoices, separately.
        Map<String, Long> invoicesByStatus = invoiceRepository.findAll().stream()
                .collect(Collectors.groupingBy(invoice -> invoice.getStatus().name(), Collectors.counting()));
        assertThat(summary.invoices().byStatus()).isEqualTo(invoicesByStatus);
        assertThat(summary.invoices().automaticByStatus()).isEqualTo(invoicesByStatus);
        assertThat(summary.invoices().awaitingApproval()).isEqualTo(invoicesByStatus.get("PENDING_APPROVAL"));
        assertThat(summary.invoices().sent()).isEqualTo(1);
        assertThat(summary.invoices().readyForDelivery()).isEqualTo(1);
        assertThat(summary.invoices().approvedWithoutDocument()).isZero();
        assertThat(summary.receivables().total()).isEqualTo(1);
        assertThat(summary.receivables().sentWithReceivable()).isEqualTo(1);
        assertThat(summary.receivables().sentWithoutReceivable()).isZero();
        assertThat(summary.occurrences().dueWithoutRun()).isZero();
        assertThat(summary.occurrences().overdueWithoutRun()).isZero();
    }

    @Test
    void successfulDelivery_correspondsToExactlyOneReceivable_forItsGrandTotal() {
        Pipeline pipeline = billFixedPriceAndProcessInvoices();
        Invoice sent = pipeline.sent;

        // A second ordinary delivery of the same invoice is refused (G-19).
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> deliveryService.sendInvoice(sent.getInvoiceId()))
                .hasMessageContaining("already been sent");

        List<Receivable> receivables = receivableRepository.findAll().stream()
                .filter(r -> r.getInvoice().getInvoiceId().equals(sent.getInvoiceId())).toList();
        assertThat(receivables).singleElement().satisfies(receivable -> {
            assertThat(receivable.getInvoiceAmount()).isEqualByComparingTo(sent.getGrandTotal());
            assertThat(receivable.getOutstandingBalance()).isEqualByComparingTo(sent.getGrandTotal());
        });
        assertThat(reportService.reconcile().totalFindings()).isZero();
        assertThat(reportService.getSummary(1).receivables().sentWithReceivable()).isEqualTo(1);
    }

    // ================================================================ reconciliation detects discrepancies

    @Test
    void reconciliation_detectsDeliberatelyIntroducedDiscrepancies() {
        Pipeline pipeline = billFixedPriceAndProcessInvoices();
        Invoice sent = pipeline.sent;               // READY_TO_SEND, sent, receivable
        Invoice ready = pipeline.readyToSend;       // READY_TO_SEND, not sent
        Invoice rejected = pipeline.rejected;       // REJECTED
        List<Invoice> pending = pipeline.pending;   // PENDING_APPROVAL
        assertThat(pending).hasSizeGreaterThanOrEqualTo(2);
        Invoice pendingA = pending.get(0);
        Invoice pendingB = pending.get(1);

        // Receivable: balance and amount changed out of band.
        jdbc.update("UPDATE receivable SET outstanding_balance = outstanding_balance - 100 WHERE invoice_id = ?",
                sent.getInvoiceId());
        jdbc.update("UPDATE receivable SET invoice_amount = invoice_amount + 1 WHERE invoice_id = ?",
                sent.getInvoiceId());
        // Sent invoice whose receivable is missing: send the READY_TO_SEND one, then remove its receivable.
        deliveryService.sendInvoice(ready.getInvoiceId());
        jdbc.update("DELETE FROM receivable WHERE invoice_id = ?", ready.getInvoiceId());
        // Document of a READY_TO_SEND invoice lost.
        jdbc.update("DELETE FROM invoice_document WHERE invoice_id = ?", sent.getInvoiceId());
        // Line items no longer add up: 5.00 off (HIGH) and one paisa off (rounding pattern, MEDIUM).
        jdbc.update("UPDATE invoice_item SET amount = amount - 5 WHERE invoice_id = ?", pendingA.getInvoiceId());
        jdbc.update("UPDATE invoice_item SET amount = amount - 0.01 WHERE invoice_id = ?", pendingB.getInvoiceId());
        // Grand total no longer subtotal + tax (and no longer the tax calculation's).
        jdbc.update("UPDATE invoice SET grand_total = grand_total + 1 WHERE invoice_id = ?", rejected.getInvoiceId());
        // Tax breakdown no longer adds up.
        jdbc.update("UPDATE invoice_tax_component SET tax_amount = tax_amount + 1 WHERE invoice_id = ?",
                pendingB.getInvoiceId());
        // Occurrence amount changed after invoicing.
        jdbc.update("UPDATE billing_schedule SET billing_amount = billing_amount + 10 WHERE billing_schedule_id = ?",
                pendingA.getBillingScheduleId());
        // Occurrence of an invoice no longer marked invoiced.
        jdbc.update("UPDATE billing_schedule SET is_invoiced = FALSE WHERE billing_schedule_id = ?",
                rejected.getBillingScheduleId());
        // Run lost its invoice link; another run points at a different occurrence's invoice.
        jdbc.update("UPDATE automatic_billing_run SET invoice_id = NULL WHERE billing_schedule_id = ?",
                pendingA.getBillingScheduleId());
        jdbc.update("UPDATE automatic_billing_run SET invoice_id = ? WHERE billing_schedule_id = ?",
                pendingB.getInvoiceId(), rejected.getBillingScheduleId());
        // A rejected-status invoice that was sent and has a document.
        jdbc.update("UPDATE invoice SET status = 'REJECTED' WHERE invoice_id = ?", ready.getInvoiceId());

        // A second automatic configuration whose due occurrences were never picked up (scheduler not run),
        // one of them wrongly flagged invoiced.
        List<BillingSchedule> unprocessed = fixedPriceConfigurationWithOccurrences(new BigDecimal("50000.00"));
        List<BillingSchedule> unprocessedDue = unprocessed.stream()
                .filter(o -> o.getBillingDate().isBefore(today)).toList();
        assertThat(unprocessedDue).hasSizeGreaterThanOrEqualTo(2);
        jdbc.update("UPDATE billing_schedule SET is_invoiced = TRUE WHERE billing_schedule_id = ?",
                unprocessedDue.get(0).getBillingScheduleId());

        Map<String, String> before = financialState();
        ReconciliationReportDto report = reportService.reconcile();
        assertThat(financialState()).as("reconciliation must not modify records").isEqualTo(before);

        Map<ReconciliationCheck, CheckResult> checks = report.checks().stream()
                .collect(Collectors.toMap(CheckResult::check, Function.identity()));
        assertDetected(checks, ReconciliationCheck.RECEIVABLE_BALANCE_UNEXPECTED, receivableIdOf(sent));
        assertDetected(checks, ReconciliationCheck.RECEIVABLE_AMOUNT_MISMATCH, receivableIdOf(sent));
        assertDetected(checks, ReconciliationCheck.SENT_WITHOUT_RECEIVABLE, ready.getInvoiceId());
        assertDetected(checks, ReconciliationCheck.READY_TO_SEND_WITHOUT_DOCUMENT, sent.getInvoiceId());
        assertDetected(checks, ReconciliationCheck.INVOICE_LINES_MISMATCH, pendingA.getInvoiceId());
        assertDetected(checks, ReconciliationCheck.INVOICE_LINES_MISMATCH, pendingB.getInvoiceId());
        assertDetected(checks, ReconciliationCheck.INVOICE_TOTAL_MISMATCH, rejected.getInvoiceId());
        assertDetected(checks, ReconciliationCheck.INVOICE_TAX_CALCULATION_MISMATCH, rejected.getInvoiceId());
        assertDetected(checks, ReconciliationCheck.INVOICE_TAX_BREAKDOWN_MISMATCH, pendingB.getInvoiceId());
        assertDetected(checks, ReconciliationCheck.OCCURRENCE_AMOUNT_MISMATCH, pendingA.getInvoiceId());
        assertDetected(checks, ReconciliationCheck.OCCURRENCE_NOT_MARKED_INVOICED, rejected.getInvoiceId());
        assertDetected(checks, ReconciliationCheck.RUN_WITHOUT_INVOICE,
                runIdOf(pendingA.getBillingScheduleId()));
        assertDetected(checks, ReconciliationCheck.RUN_INVOICE_OCCURRENCE_MISMATCH,
                runIdOf(rejected.getBillingScheduleId()));
        assertDetected(checks, ReconciliationCheck.INVOICE_LINKED_TO_MULTIPLE_RUNS, pendingB.getInvoiceId());
        assertDetected(checks, ReconciliationCheck.SENT_INVOICE_NOT_APPROVED, ready.getInvoiceId());
        assertDetected(checks, ReconciliationCheck.DOCUMENT_FOR_UNAPPROVED_INVOICE, ready.getInvoiceId());
        assertDetected(checks, ReconciliationCheck.OCCURRENCE_INVOICED_WITHOUT_INVOICE,
                unprocessedDue.get(0).getBillingScheduleId());
        assertDetected(checks, ReconciliationCheck.OCCURRENCE_OVERDUE_WITHOUT_RUN,
                unprocessedDue.get(1).getBillingScheduleId());

        // Severity: 5.00 off keeps HIGH; one paisa per line is the known rounding pattern.
        assertThat(severityOf(checks, ReconciliationCheck.INVOICE_LINES_MISMATCH, pendingA.getInvoiceId()))
                .isEqualTo(Severity.HIGH);
        assertThat(severityOf(checks, ReconciliationCheck.INVOICE_LINES_MISMATCH, pendingB.getInvoiceId()))
                .isEqualTo(Severity.MEDIUM);
        assertThat(report.highOrCriticalFindings()).isLessThan(report.totalFindings());

        // Untouched checks stay clean.
        assertThat(checks.get(ReconciliationCheck.DUPLICATE_RECEIVABLE).findingCount()).isZero();
        assertThat(checks.get(ReconciliationCheck.DUPLICATE_INVOICE_FOR_SOURCE).findingCount()).isZero();

        // Exception records carry what Finance/engineering need to act on.
        assertThat(report.checks()).allSatisfy(check -> {
            assertThat(check.owner()).isNotNull();
            assertThat(check.businessImpact()).isNotBlank();
            assertThat(check.correctiveAction()).isNotBlank();
        });
        CheckResult balance = checks.get(ReconciliationCheck.RECEIVABLE_BALANCE_UNEXPECTED);
        assertThat(balance.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.reference()).isEqualTo(sent.getInvoiceNumber());
            assertThat(finding.relatedId()).isEqualTo(sent.getInvoiceId());
        });

        // The summary reports the same out-of-band state.
        BillingOperationsSummaryDto summary = reportService.getSummary(30);
        assertThat(summary.receivables().sentWithoutReceivable()).isEqualTo(1);
        assertThat(summary.occurrences().overdueWithoutRun()).isEqualTo(unprocessedDue.size() - 1);
        assertThat(summary.occurrences().oldestOverdueBillingDate())
                .isEqualTo(unprocessedDue.get(1).getBillingDate());
    }

    // ================================================================ run monitoring and alerts

    @Test
    void runMonitoring_andAlerts_followConfiguredThresholdsOnly() {
        BillingConfiguration configuration = configuration();
        LocalDateTime now = LocalDateTime.now();
        saveRun(configuration, 1, AutomaticBillingRunStatus.FAILED, AutomaticBillingStep.TAX_CALCULATION, 3, null, null);
        saveRun(configuration, 2, AutomaticBillingRunStatus.FAILED, AutomaticBillingStep.SOURCE_DATA_ACQUISITION, 1,
                now.plusMinutes(15), null);
        saveRun(configuration, 3, AutomaticBillingRunStatus.PROCESSING, null, 1, null, now.minusMinutes(5));
        saveRun(configuration, 4, AutomaticBillingRunStatus.COMPLETED, AutomaticBillingStep.INVOICE_CREATION, 2,
                null, null);

        BillingOperationsSummaryDto.RunSection runs = reportService.getSummary(7).automaticBillingRuns();
        assertThat(runs.byStatus()).containsEntry("FAILED", 2L).containsEntry("PROCESSING", 1L)
                .containsEntry("COMPLETED", 1L);
        assertThat(runs.needingIntervention()).isEqualTo(1);
        assertThat(runs.needingInterventionByFailureStep()).containsExactly(Map.entry("TAX_CALCULATION", 1L));
        assertThat(runs.retryScheduled()).isEqualTo(1);
        assertThat(runs.retryScheduledByFailureStep()).containsExactly(Map.entry("SOURCE_DATA_ACQUISITION", 1L));
        assertThat(runs.retriesExhausted()).isEqualTo(1);
        assertThat(runs.retried()).isEqualTo(2);
        assertThat(runs.recovered()).isEqualTo(1);
        assertThat(runs.stuck()).isEqualTo(1);

        // No threshold configured: nothing is evaluated, nothing raised.
        assertThat(reportService.evaluateAlerts()).isEmpty();
        assertThat(alertJob.evaluate()).isEmpty();

        properties.getAlerts().getThresholds().put(OperationsAlertCondition.RUNS_NEEDING_INTERVENTION, 0L);
        properties.getAlerts().getThresholds().put(OperationsAlertCondition.RUNS_STUCK, 5L);
        properties.getAlerts().getThresholds().put(OperationsAlertCondition.RECONCILIATION_EXCEPTIONS, 0L);

        Map<OperationsAlertCondition, OperationsAlertDto> evaluated = new EnumMap<>(OperationsAlertCondition.class);
        reportService.evaluateAlerts().forEach(alert -> evaluated.put(alert.condition(), alert));
        assertThat(evaluated).containsOnlyKeys(OperationsAlertCondition.RUNS_NEEDING_INTERVENTION,
                OperationsAlertCondition.RUNS_STUCK, OperationsAlertCondition.RECONCILIATION_EXCEPTIONS);
        assertThat(evaluated.get(OperationsAlertCondition.RUNS_NEEDING_INTERVENTION).raised()).isTrue();
        assertThat(evaluated.get(OperationsAlertCondition.RUNS_STUCK).raised()).isFalse();
        // COMPLETED run without an invoice is a reconciliation exception.
        assertThat(evaluated.get(OperationsAlertCondition.RECONCILIATION_EXCEPTIONS).raised()).isTrue();

        double before = alertCount(OperationsAlertCondition.RUNS_NEEDING_INTERVENTION);
        List<OperationsAlertDto> raised = alertJob.evaluate();
        assertThat(raised).extracting(OperationsAlertDto::condition).containsExactlyInAnyOrder(
                OperationsAlertCondition.RUNS_NEEDING_INTERVENTION, OperationsAlertCondition.RECONCILIATION_EXCEPTIONS);
        assertThat(alertCount(OperationsAlertCondition.RUNS_NEEDING_INTERVENTION)).isEqualTo(before + 1);

        // Scheduled evaluation stays off unless enabled.
        assertThat(properties.getAlerts().isEnabled()).isFalse();
        alertJob.scheduledEvaluation();
        assertThat(alertCount(OperationsAlertCondition.RUNS_NEEDING_INTERVENTION)).isEqualTo(before + 1);
    }

    // ================================================================ read-only

    @Test
    void repeatedMonitoringAndReconciliation_doNotModifyFinancialRecords() throws Exception {
        billFixedPriceAndProcessInvoices();
        properties.getAlerts().getThresholds().put(OperationsAlertCondition.RECONCILIATION_EXCEPTIONS, 0L);
        Map<String, String> before = financialState();

        for (int i = 0; i < 3; i++) {
            reportService.getSummary(30);
            reportService.reconcile();
            reportService.evaluateAlerts();
            alertJob.evaluate();
            for (String path : new String[] {"/api/v1/operations/billing-summary", "/api/v1/operations/reconciliation",
                    "/api/v1/operations/alerts"}) {
                assertThat(mockMvc.perform(get(path).with(operationsUser())).andReturn().getResponse().getStatus())
                        .isEqualTo(200);
            }
        }

        assertThat(financialState()).isEqualTo(before);
    }

    // ================================================================ access control

    @Test
    void operationsReports_requireAConfiguredAuthority() throws Exception {
        String[] paths = {"/api/v1/operations/billing-summary", "/api/v1/operations/reconciliation",
                "/api/v1/operations/alerts"};
        for (String path : paths) {
            assertThat(status(get(path))).as(path).isEqualTo(401);
            assertThat(status(get(path).with(jwt()))).as(path).isEqualTo(403);
            assertThat(status(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SALES")))))
                    .as(path).isEqualTo(403);
            assertThat(status(get(path).with(operationsUser()))).as(path).isEqualTo(200);
        }

        // Nobody is granted until an authority is configured.
        properties.setReportAuthorities(List.of());
        assertThat(status(get(paths[0]).with(operationsUser()))).isEqualTo(403);
        assertThat(new OperationsMonitoringProperties().getReportAuthorities()).isEmpty();

        // Other APIs keep their existing rule (any authenticated user).
        assertThat(status(get("/api/v1/invoices").with(jwt()))).isEqualTo(200);
    }

    @Test
    void summary_rejectsAnOutOfRangeWindow() throws Exception {
        assertThat(status(get("/api/v1/operations/billing-summary?days=0").with(operationsUser()))).isEqualTo(400);
        assertThat(status(get("/api/v1/operations/billing-summary?days=367").with(operationsUser()))).isEqualTo(400);
    }

    // ================================================================ representative volume

    @Test
    void operationalQueries_atRepresentativeVolume() {
        int invoices = 3_000;
        tx.executeWithoutResult(status -> {
            for (int i = 0; i < invoices; i++) {
                LocalDate period = today.minusMonths(1);
                Invoice invoice = Invoice.builder()
                        .invoiceNumber(String.format("PERF-%06d", i))
                        .taxCalculationId(UUID.randomUUID()) // deliberately unmatched: one finding per invoice
                        .clientId(client.getClientId())
                        .projectId(project.getPmsProjectId())
                        .billingPeriodStart(period.withDayOfMonth(1))
                        .billingPeriodEnd(period.withDayOfMonth(period.lengthOfMonth()))
                        .currencyCode("INR")
                        .subtotal(new BigDecimal("1000.00"))
                        .totalTaxAmount(BigDecimal.ZERO)
                        .grandTotal(new BigDecimal("1000.00"))
                        .invoiceDate(today)
                        .generatedAt(LocalDateTime.now())
                        .status(i % 3 == 0 ? InvoiceStatus.READY_TO_SEND : InvoiceStatus.PENDING_APPROVAL)
                        .items(new ArrayList<>())
                        .taxComponents(new ArrayList<>())
                        .build();
                for (int line = 0; line < 2; line++) {
                    invoice.getItems().add(InvoiceItem.builder().invoice(invoice).itemType(BillingItemType.FIXED_PRICE)
                            .itemName("Line " + line).amount(new BigDecimal("500.00")).build());
                }
                invoiceRepository.save(invoice);
                if (i % 3 == 0) {
                    deliveryRepository.save(InvoiceDelivery.builder().invoiceId(invoice.getInvoiceId())
                            .recipientEmail("ap@example.com").status(InvoiceDeliveryStatus.SENT)
                            .sentAt(LocalDateTime.now()).retryCount(0).build());
                    receivableRepository.save(Receivable.builder().invoice(invoice)
                            .invoiceNumber(invoice.getInvoiceNumber()).clientId(client.getClientId())
                            .projectId(project.getPmsProjectId()).invoiceDate(today)
                            .invoiceAmount(invoice.getGrandTotal()).outstandingBalance(invoice.getGrandTotal())
                            .status(ReceivableStatus.OUTSTANDING).build());
                }
            }
        });

        long start = System.nanoTime();
        BillingOperationsSummaryDto summary = reportService.getSummary(30);
        long summaryMillis = (System.nanoTime() - start) / 1_000_000;
        start = System.nanoTime();
        ReconciliationReportDto report = reportService.reconcile();
        long reconcileMillis = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("[Phase15Volume] invoices=%d lines=%d receivables=%d summary=%dms reconciliation=%dms%n",
                invoices, invoices * 2, invoices / 3, summaryMillis, reconcileMillis);

        assertThat(summary.invoices().total()).isEqualTo(invoices);
        assertThat(summary.receivables().sentWithReceivable()).isEqualTo(invoices / 3);
        // Counts are complete; the listed findings are bounded.
        CheckResult missingTax = report.checks().stream()
                .filter(check -> check.check() == ReconciliationCheck.INVOICE_TAX_CALCULATION_MISMATCH)
                .findFirst().orElseThrow();
        assertThat(missingTax.findingCount()).isEqualTo(invoices);
        assertThat(missingTax.findings()).hasSize(properties.getReconciliation().getMaxFindingsPerCheck());
        assertThat(missingTax.truncated()).isTrue();
        // Development-machine bound only; the measured times are printed above.
        assertThat(summaryMillis + reconcileMillis).isLessThan(30_000);
    }

    // ================================================================ pipeline fixture

    /** Invoices of one configuration after the real pipeline, by business status. */
    private static final class Pipeline {
        List<Invoice> invoices;
        Invoice sent;
        Invoice readyToSend;
        Invoice rejected;
        List<Invoice> pending;
    }

    /**
     * Fixed price, 8 monthly payments from 5 months ago: the scheduler
     * invoices every due payment; then one invoice is approved and sent, one
     * approved only, one rejected, the rest left pending.
     */
    private Pipeline billFixedPriceAndProcessInvoices() {
        List<BillingSchedule> occurrences = fixedPriceConfigurationWithOccurrences(new BigDecimal("800000.00"));
        long due = occurrences.stream().filter(o -> !o.getBillingDate().isAfter(today)).count();
        assertThat(due).isGreaterThanOrEqualTo(5);
        scheduler.runOnce();

        List<UUID> occurrenceIds = occurrences.stream().map(BillingSchedule::getBillingScheduleId).toList();
        List<Invoice> invoices = invoiceRepository.findAll().stream()
                .filter(invoice -> occurrenceIds.contains(invoice.getBillingScheduleId()))
                .sorted(Comparator.comparing(Invoice::getBillingPeriodStart))
                .toList();
        assertThat(invoices).hasSize((int) due)
                .allSatisfy(invoice -> assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING_APPROVAL));

        Pipeline pipeline = new Pipeline();
        pipeline.sent = approve(invoices.get(0));
        deliveryService.sendInvoice(pipeline.sent.getInvoiceId());
        pipeline.readyToSend = approve(invoices.get(1));
        lockService.acquireLock(LockResourceType.INVOICE, invoices.get(2).getInvoiceId(), LockActionType.APPROVAL);
        InvoiceRejectionRequestDto rejection = new InvoiceRejectionRequestDto();
        rejection.setReason("Incorrect purchase order reference.");
        invoiceService.rejectInvoice(invoices.get(2).getInvoiceId(), rejection);
        pipeline.rejected = invoiceRepository.findById(invoices.get(2).getInvoiceId()).orElseThrow();
        pipeline.pending = invoices.subList(3, invoices.size());
        pipeline.invoices = invoices;
        return pipeline;
    }

    private Invoice approve(Invoice invoice) {
        lockService.acquireLock(LockResourceType.INVOICE, invoice.getInvoiceId(), LockActionType.APPROVAL);
        approvalController.approve(invoice.getInvoiceId());
        Invoice approved = invoiceRepository.findById(invoice.getInvoiceId()).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(InvoiceStatus.READY_TO_SEND);
        return approved;
    }

    private List<BillingSchedule> fixedPriceConfigurationWithOccurrences(BigDecimal contractValue) {
        BillingConfiguration configuration = configuration();
        tx.executeWithoutResult(status -> fixedPriceRepository.save(BillingFixedPriceConfiguration.builder()
                .billingConfiguration(configuration).contractValue(contractValue)
                .contractValueSource(ContractValueSource.MANUAL).isActive(true).build()));
        tx.executeWithoutResult(status ->
                occurrenceService.generateOccurrencesForFixedPrice(configuration.getBillingConfigurationId()));
        return scheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(configuration);
    }

    private BillingConfiguration configuration() {
        LocalDate from = today.minusMonths(5).withDayOfMonth(1);
        return tx.execute(status -> {
            BillingTypeMaster type = billingTypeRepository.findByBillingTypeNameIgnoreCase("Fixed Price")
                    .orElseGet(() -> billingTypeRepository.save(BillingTypeMaster.builder()
                            .billingTypeName("Fixed Price").isActive(true).build()));
            return configurationRepository.save(BillingConfiguration.builder()
                    .client(client)
                    .project(project)
                    .billingType(type)
                    .billingFrequency(monthly)
                    .currency(inr)
                    .taxRegion(karnataka)
                    .paymentTerm(net30)
                    .effectiveFrom(from)
                    .effectiveTo(from.plusMonths(8).minusDays(1))
                    .approvalStatus(ApprovalStatus.APPROVED)
                    .billingStatus(BillingConfigurationStatus.ACTIVE)
                    .invoiceGenerationType(InvoiceGenerationType.AUTOMATIC)
                    .manuallyDeactivated(false)
                    .build());
        });
    }

    private void saveRun(BillingConfiguration configuration, int month, AutomaticBillingRunStatus status,
            AutomaticBillingStep failureStep, int attempts, LocalDateTime nextRetryAt, LocalDateTime claimExpiresAt) {
        LocalDate start = today.minusMonths(month).withDayOfMonth(1);
        tx.executeWithoutResult(s -> runRepository.save(AutomaticBillingRun.builder()
                .billingConfiguration(configurationRepository.getReferenceById(configuration.getBillingConfigurationId()))
                .billingPeriodStart(start)
                .billingPeriodEnd(start.withDayOfMonth(start.lengthOfMonth()))
                .status(status)
                .failureStep(failureStep)
                .failureReason(failureStep == null ? null : "test failure")
                .attemptCount(attempts)
                .nextRetryAt(nextRetryAt)
                .claimToken(claimExpiresAt == null ? null : UUID.randomUUID())
                .claimExpiresAt(claimExpiresAt)
                .build()));
    }

    // ================================================================ helpers

    private void seedMasterData() {
        inr = currencyRepository.save(CurrencyMaster.builder().currencyCode("INR").currencyName("Indian Rupee")
                .currencySymbol("₹").isActive(true).build());
        karnataka = taxRegionRepository.save(TaxRegionMaster.builder()
                .taxRegionCode("KA").taxRegionName("Karnataka").currencyCode("INR").isActive(true).build());
        TaxConfiguration gst = TaxConfiguration.builder().taxRegion(karnataka).effectiveFrom(LocalDate.of(2020, 1, 1))
                .isActive(true).components(new ArrayList<>()).build();
        for (String[] component : new String[][] {{"CGST", "9.00", "SAME_JURISDICTION"},
                {"SGST", "9.00", "SAME_JURISDICTION"}, {"IGST", "18.00", "DIFFERENT_JURISDICTION"}}) {
            TaxTypeMaster type = taxTypeRepository.save(TaxTypeMaster.builder()
                    .taxTypeCode(component[0]).taxTypeName(component[0]).isActive(true).build());
            gst.getComponents().add(TaxConfigurationComponent.builder().taxConfiguration(gst).taxType(type)
                    .taxRate(new BigDecimal(component[1]))
                    .applicabilityType(TaxApplicabilityType.valueOf(component[2])).isActive(true).build());
        }
        taxConfigurationRepository.save(gst);
        net30 = paymentTermsRepository.save(PaymentTermsMaster.builder()
                .paymentTermName("Net 30").paymentDays(30).isActive(true).build());
        monthly = frequencyRepository.save(BillingFrequencyMaster.builder().billingFrequencyName("Monthly")
                .durationUnit(RenewalDurationUnit.MONTHS).durationValue(1).isActive(true).build());
        companyProfileRepository.save(CompanyProfile.builder().legalName("Paves Technologies Pvt Ltd")
                .state("Karnataka").country("India").gstin("29ABCDE1234F1Z5").email("billing@paves.example")
                .isActive(true).build());
        client = clientRepository.save(Client.builder().clientId(UUID.randomUUID()).clientName("Acme Retail")
                .email("ap@acme.example").build());
        project = projectRepository.save(ProjectMasterReference.builder().pmsProjectId(4242L)
                .clientId(client.getClientId()).projectName("Store Modernisation").primaryLocation("Karnataka")
                .build());
    }

    /** Every table emptied, so each test sees only its own records. In-memory test database only. */
    private void emptyDatabase() {
        List<String> tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = CURRENT_SCHEMA AND table_type = 'BASE TABLE'", String.class);
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            tables.forEach(table -> jdbc.execute("TRUNCATE TABLE \"" + table + "\""));
        } finally {
            jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    /** Full content of the financial tables, blobs as hex - compared before and after monitoring. */
    private Map<String, String> financialState() {
        Map<String, String> state = new LinkedHashMap<>();
        for (String table : FINANCIAL_TABLES) {
            state.put(table, jdbc.queryForList("SELECT * FROM " + table).stream()
                    .map(row -> row.entrySet().stream()
                            .map(e -> e.getKey() + "=" + (e.getValue() instanceof byte[] bytes
                                    ? HexFormat.of().formatHex(bytes) : String.valueOf(e.getValue())))
                            .sorted()
                            .collect(Collectors.joining(",")))
                    .sorted()
                    .collect(Collectors.joining("\n")));
        }
        return state;
    }

    private void assertDetected(Map<ReconciliationCheck, CheckResult> checks, ReconciliationCheck check, UUID recordId) {
        assertThat(checks.get(check).findings())
                .as(check + " should report " + recordId)
                .anySatisfy(finding -> assertThat(finding.recordId()).isEqualTo(recordId));
    }

    private Severity severityOf(Map<ReconciliationCheck, CheckResult> checks, ReconciliationCheck check, UUID recordId) {
        return checks.get(check).findings().stream().filter(f -> f.recordId().equals(recordId))
                .findFirst().orElseThrow().severity();
    }

    private UUID receivableIdOf(Invoice invoice) {
        return receivableRepository.findByInvoiceId(invoice.getInvoiceId()).orElseThrow().getReceivableId();
    }

    private UUID runIdOf(UUID occurrenceId) {
        return runRepository.findByBillingScheduleId(occurrenceId).orElseThrow().getAutomaticBillingRunId();
    }

    private double alertCount(OperationsAlertCondition condition) {
        Counter counter = meterRegistry.find("ar.operations.alerts").tag("condition", condition.name()).counter();
        return counter == null ? 0 : counter.count();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor operationsUser() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_AR_OPERATIONS"));
    }

    private int status(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }
}
