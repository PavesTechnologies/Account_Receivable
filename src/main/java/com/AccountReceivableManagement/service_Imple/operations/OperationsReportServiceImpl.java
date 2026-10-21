package com.AccountReceivableManagement.service_Imple.operations;

import com.AccountReceivableManagement.config.properties.AutomaticBillingSchedulerProperties;
import com.AccountReceivableManagement.config.properties.OperationsMonitoringProperties;
import com.AccountReceivableManagement.dto.operations.BillingOperationsSummaryDto;
import com.AccountReceivableManagement.dto.operations.BillingOperationsSummaryDto.DateStatusCount;
import com.AccountReceivableManagement.dto.operations.BillingOperationsSummaryDto.TypeStatusCount;
import com.AccountReceivableManagement.dto.operations.OperationsAlertDto;
import com.AccountReceivableManagement.dto.operations.ReconciliationReportDto;
import com.AccountReceivableManagement.dto.operations.ReconciliationReportDto.CheckResult;
import com.AccountReceivableManagement.dto.operations.ReconciliationReportDto.Finding;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.entity_enums.operations.OperationsAlertCondition;
import com.AccountReceivableManagement.entity_enums.operations.ReconciliationCheck;
import com.AccountReceivableManagement.entity_enums.operations.ReconciliationCheck.RecordType;
import com.AccountReceivableManagement.entity_enums.operations.ReconciliationCheck.Severity;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.automatic_billing.AutomaticBillingRunRepository;
import com.AccountReceivableManagement.repo.operations.OperationsReportRepository;
import com.AccountReceivableManagement.service_interface.operations.OperationsReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Read-only: every method runs in a read-only transaction and only issues
 * the SELECTs of {@link OperationsReportRepository}. Discrepancies are
 * reported, never corrected - corrections go through the existing
 * authorized processes (invoice correction, receivable recovery).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OperationsReportServiceImpl implements OperationsReportService {

    /** Same per-line rounding tolerance as invoice document validation (InvoiceServiceImpl). */
    static final BigDecimal LINE_ROUNDING_TOLERANCE_PER_ITEM = new BigDecimal("0.01");

    private final OperationsReportRepository queries;
    private final AutomaticBillingRunRepository runRepository;
    private final OperationsMonitoringProperties properties;
    private final AutomaticBillingSchedulerProperties schedulerProperties;
    private final Clock clock = Clock.systemDefaultZone();

    // ================================================================ summary

    @Override
    public BillingOperationsSummaryDto getSummary(int windowDays) {
        if (windowDays < 1 || windowDays > 366) {
            throw new GlobalExceptionHandler.ValidationException("days must be between 1 and 366.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        return new BillingOperationsSummaryDto(now, runSection(now, windowDays), occurrenceSection(today),
                invoiceSection(), receivableSection());
    }

    private BillingOperationsSummaryDto.RunSection runSection(LocalDateTime now, int windowDays) {
        Map<String, Long> byStatus = countsByKey(queries.runCountsByStatus());
        List<DateStatusCount> byDate = queries
                .runCountsByCreatedDateAndStatus(now.toLocalDate().minusDays(windowDays - 1L).atStartOfDay())
                .stream()
                .map(row -> new DateStatusCount(toLocalDate(row[0]), name(row[1]), toLong(row[2])))
                .sorted(Comparator.comparing(DateStatusCount::date).thenComparing(DateStatusCount::status))
                .toList();
        return new BillingOperationsSummaryDto.RunSection(
                byStatus.values().stream().mapToLong(Long::longValue).sum(),
                byStatus,
                typeStatusCounts(queries.runCountsByBillingTypeAndStatus()),
                windowDays,
                byDate,
                countsByKey(queries.failedRunsByStep(false)),
                countsByKey(queries.failedRunsByStep(true)),
                runRepository.countByStatusAndNextRetryAtIsNull(AutomaticBillingRunStatus.FAILED),
                runRepository.countByStatusAndNextRetryAtIsNotNull(AutomaticBillingRunStatus.FAILED),
                queries.countRetriesExhausted(schedulerProperties.getRetry().getMaxAttempts()),
                queries.countRetriedRuns(),
                queries.countRecoveredRuns(),
                runRepository.countByStatusAndClaimExpiresAtBefore(AutomaticBillingRunStatus.PROCESSING, now));
    }

    private BillingOperationsSummaryDto.OccurrenceSection occurrenceSection(LocalDate today) {
        LocalDate overdueAsOf = overdueAsOf(today);
        return new BillingOperationsSummaryDto.OccurrenceSection(
                queries.countDueOccurrencesWithoutRun(today),
                queries.countDueOccurrencesWithoutRun(overdueAsOf),
                properties.getOverdueGraceDays(),
                queries.oldestDueOccurrenceWithoutRun(overdueAsOf));
    }

    private BillingOperationsSummaryDto.InvoiceSection invoiceSection() {
        Map<String, Long> byStatus = countsByKey(queries.invoiceCountsByStatus());
        return new BillingOperationsSummaryDto.InvoiceSection(
                byStatus.values().stream().mapToLong(Long::longValue).sum(),
                byStatus,
                typeStatusCounts(queries.invoiceCountsByBillingTypeAndStatus()),
                countsByKey(queries.automaticInvoiceCountsByStatus()),
                byStatus.getOrDefault(InvoiceStatus.PENDING_APPROVAL.name(), 0L),
                queries.countApprovedWithoutDocument(),
                queries.countReadyForDelivery(),
                queries.countSentInvoices(),
                queries.countDeliveryFailedNotSent(),
                queries.countFailedDeliveryAttempts());
    }

    private BillingOperationsSummaryDto.ReceivableSection receivableSection() {
        Map<String, Long> byStatus = countsByKey(queries.receivableCountsByStatus());
        return new BillingOperationsSummaryDto.ReceivableSection(
                byStatus.values().stream().mapToLong(Long::longValue).sum(),
                byStatus,
                queries.countSentInvoices(true),
                queries.countSentInvoices(false));
    }

    /** An occurrence is overdue once its billing date is {@code overdueGraceDays} or more days in the past. */
    private LocalDate overdueAsOf(LocalDate today) {
        return today.minusDays(properties.getOverdueGraceDays());
    }

    // ================================================================ reconciliation

    @Override
    public ReconciliationReportDto reconcile() {
        LocalDateTime now = LocalDateTime.now(clock);
        int limit = properties.getReconciliation().getMaxFindingsPerCheck();
        List<CheckResult> checks = new ArrayList<>();

        // Source amounts
        CheckCollector sourceMissing = new CheckCollector(ReconciliationCheck.INVOICE_SOURCE_MISSING, limit);
        CheckCollector occurrenceMissing = new CheckCollector(ReconciliationCheck.OCCURRENCE_MISSING, limit);
        CheckCollector occurrenceAmount = new CheckCollector(ReconciliationCheck.OCCURRENCE_AMOUNT_MISMATCH, limit);
        queries.occurrenceAmountDiscrepancies(row -> {
            if (row[3] == null) {
                occurrenceMissing.add(invoiceFinding(row, (UUID) row[2], "existing occurrence", "missing", null));
            } else {
                occurrenceAmount.add(invoiceFinding(row, (UUID) row[2], text(row[4]), text(row[5]),
                        "occurrence amount vs invoice subtotal"));
            }
        });
        CheckCollector snapshotAmount = new CheckCollector(ReconciliationCheck.SNAPSHOT_AMOUNT_MISMATCH, limit);
        queries.snapshotAmountDiscrepancies(row -> {
            if (row[2] == null) {
                sourceMissing.add(invoiceFinding(row, null, "occurrence or snapshot", "none", null));
            } else if (row[3] == null) {
                sourceMissing.add(invoiceFinding(row, (UUID) row[2], "existing snapshot", "missing", null));
            } else {
                snapshotAmount.add(invoiceFinding(row, (UUID) row[2], text(row[4]), text(row[5]),
                        "snapshot total vs invoice subtotal"));
            }
        });
        CheckCollector snapshotItems = new CheckCollector(ReconciliationCheck.SNAPSHOT_ITEMS_MISMATCH, limit);
        queries.snapshotItemDiscrepancies(row -> snapshotItems.add(new Finding(RecordType.BILLING_SNAPSHOT,
                (UUID) row[0], text(row[1]), null, text(row[2]), text(row[3]),
                lineSeverity(ReconciliationCheck.SNAPSHOT_ITEMS_MISMATCH, row[2], row[3], row[4]),
                "snapshot total vs sum of " + toLong(row[4]) + " line(s)")));
        checks.add(sourceMissing.result());
        checks.add(occurrenceMissing.result());
        checks.add(occurrenceAmount.result());
        checks.add(snapshotAmount.result());
        checks.add(snapshotItems.result());

        // Invoice totals and tax
        checks.add(collect(ReconciliationCheck.INVOICE_LINES_MISMATCH, limit, queries::invoiceLineDiscrepancies,
                row -> invoiceFinding(row, null, text(row[2]), text(row[3]),
                        toLong(row[4]) == 0 ? "invoice has no line items" : "subtotal vs sum of " + toLong(row[4])
                                + " line(s)",
                        toLong(row[4]) == 0 ? Severity.HIGH
                                : lineSeverity(ReconciliationCheck.INVOICE_LINES_MISMATCH, row[2], row[3], row[4]))));
        checks.add(collect(ReconciliationCheck.INVOICE_TOTAL_MISMATCH, limit, queries::invoiceTotalDiscrepancies,
                row -> invoiceFinding(row, null, "grand total = subtotal + tax",
                        "subtotal=" + text(row[2]) + " tax=" + text(row[3]) + " grandTotal=" + text(row[4]), null)));
        checks.add(collect(ReconciliationCheck.INVOICE_TAX_BREAKDOWN_MISMATCH, limit,
                queries::invoiceTaxBreakdownDiscrepancies,
                row -> invoiceFinding(row, null, text(row[2]), text(row[3]), "total tax vs sum of tax components")));
        checks.add(collect(ReconciliationCheck.INVOICE_TAX_CALCULATION_MISMATCH, limit,
                queries::invoiceTaxCalculationDiscrepancies,
                row -> row[3] == null
                        ? invoiceFinding(row, (UUID) row[2], "existing tax calculation", "missing", null)
                        : invoiceFinding(row, (UUID) row[2],
                                "taxable=" + text(row[7]) + " tax=" + text(row[8]) + " grandTotal=" + text(row[9]),
                                "subtotal=" + text(row[4]) + " tax=" + text(row[5]) + " grandTotal=" + text(row[6]),
                                "tax calculation (expected) vs invoice (actual)")));

        // Invoice ↔ occurrence ↔ run
        CheckCollector duplicates = new CheckCollector(ReconciliationCheck.DUPLICATE_INVOICE_FOR_SOURCE, limit);
        queries.duplicateInvoicesPerOccurrence(row -> duplicates.add(new Finding(RecordType.BILLING_OCCURRENCE,
                (UUID) row[0], null, null, "1 invoice", toLong(row[1]) + " invoices", null, "per occurrence")));
        queries.duplicateInvoicesPerSnapshot(row -> duplicates.add(new Finding(RecordType.BILLING_SNAPSHOT,
                (UUID) row[0], null, null, "1 invoice", toLong(row[1]) + " invoices", null, "per snapshot")));
        checks.add(duplicates.result());
        checks.add(collect(ReconciliationCheck.OCCURRENCE_INVOICED_WITHOUT_INVOICE, limit,
                queries::occurrencesInvoicedWithoutInvoice,
                row -> new Finding(RecordType.BILLING_OCCURRENCE, (UUID) row[0], "billing date " + text(row[1]),
                        null, "an invoice", "none", null, null)));
        checks.add(collect(ReconciliationCheck.OCCURRENCE_NOT_MARKED_INVOICED, limit,
                queries::invoicesWithOccurrenceNotMarkedInvoiced,
                row -> invoiceFinding(row, (UUID) row[2], "occurrence invoiced", "periodStatus=" + text(row[3]),
                        null)));
        checks.add(collect(ReconciliationCheck.RUN_WITHOUT_INVOICE, limit, queries::runsWithoutInvoice,
                row -> new Finding(RecordType.AUTOMATIC_BILLING_RUN, (UUID) row[0], "status " + text(row[1]),
                        (UUID) row[2], "existing invoice", row[2] == null ? "no invoice id" : "invoice missing",
                        null, null)));
        checks.add(collect(ReconciliationCheck.RUN_INVOICE_OCCURRENCE_MISMATCH, limit,
                queries::runInvoiceOccurrenceMismatches,
                row -> new Finding(RecordType.AUTOMATIC_BILLING_RUN, (UUID) row[0], text(row[3]), (UUID) row[2],
                        "occurrence " + text(row[1]), "occurrence " + text(row[4]), null, "run vs invoice occurrence")));
        checks.add(collect(ReconciliationCheck.INVOICE_LINKED_TO_MULTIPLE_RUNS, limit,
                queries::invoicesWithMultipleRuns,
                row -> new Finding(RecordType.INVOICE, (UUID) row[0], null, null, "1 run", toLong(row[1]) + " runs",
                        null, null)));
        checks.add(collect(ReconciliationCheck.OCCURRENCE_OVERDUE_WITHOUT_RUN, limit,
                sink -> queries.overdueOccurrencesWithoutRun(overdueAsOf(now.toLocalDate()), sink),
                row -> new Finding(RecordType.BILLING_OCCURRENCE, (UUID) row[0], "billing date " + text(row[1]),
                        (UUID) row[2], "a run by " + text(((LocalDate) row[1]).plusDays(properties.getOverdueGraceDays())),
                        "no run", null, "related id = billing configuration")));

        // Approval, document, delivery
        checks.add(collect(ReconciliationCheck.READY_TO_SEND_WITHOUT_DOCUMENT, limit,
                queries::readyToSendWithoutDocument, row -> statusFinding(row, "finalized document")));
        checks.add(collect(ReconciliationCheck.APPROVED_WITHOUT_DOCUMENT, limit,
                queries::approvedWithoutDocument, row -> statusFinding(row, "finalized document")));
        checks.add(collect(ReconciliationCheck.DOCUMENT_FOR_UNAPPROVED_INVOICE, limit,
                queries::documentsForUnapprovedInvoices, row -> statusFinding(row, "APPROVED or READY_TO_SEND")));
        checks.add(collect(ReconciliationCheck.SENT_INVOICE_NOT_APPROVED, limit,
                queries::sentInvoicesNotApproved, row -> statusFinding(row, "APPROVED or READY_TO_SEND")));

        // Receivables
        checks.add(collect(ReconciliationCheck.SENT_WITHOUT_RECEIVABLE, limit,
                queries::sentInvoicesWithoutReceivable,
                row -> invoiceFinding(row, null, "receivable of " + text(row[2]), "none", null)));
        checks.add(collect(ReconciliationCheck.RECEIVABLE_WITHOUT_SENT_DELIVERY, limit,
                queries::receivablesWithoutSentDelivery,
                row -> receivableFinding(row, "a SENT delivery", "none", null)));
        checks.add(collect(ReconciliationCheck.RECEIVABLE_AMOUNT_MISMATCH, limit,
                queries::receivableAmountMismatches,
                row -> receivableFinding(row, text(row[3]), text(row[4]), "invoice grand total vs receivable amount")));
        checks.add(collect(ReconciliationCheck.RECEIVABLE_BALANCE_UNEXPECTED, limit,
                queries::unexpectedReceivableBalances,
                row -> receivableFinding(row, "outstanding " + text(row[3]), "outstanding " + text(row[4]),
                        "status " + text(row[5]) + "; no payment process reduces balances yet")));
        checks.add(collect(ReconciliationCheck.DUPLICATE_RECEIVABLE, limit, queries::duplicateReceivables,
                row -> new Finding(RecordType.INVOICE, (UUID) row[0], null, null, "1 receivable",
                        toLong(row[1]) + " receivables", null, null)));

        return new ReconciliationReportDto(now, checks.size(),
                checks.stream().mapToLong(CheckResult::findingCount).sum(),
                checks.stream().mapToLong(CheckResult::highOrCriticalCount).sum(),
                checks);
    }

    // ================================================================ alerts

    @Override
    public List<OperationsAlertDto> evaluateAlerts() {
        Map<OperationsAlertCondition, Long> thresholds = new EnumMap<>(OperationsAlertCondition.class);
        properties.getAlerts().getThresholds().forEach((condition, threshold) -> {
            if (condition != null && threshold != null) {
                thresholds.put(condition, threshold);
            }
        });
        if (thresholds.isEmpty()) {
            return List.of();
        }
        BillingOperationsSummaryDto summary = getSummary(1);
        Long reconciliationExceptions = thresholds.containsKey(OperationsAlertCondition.RECONCILIATION_EXCEPTIONS)
                ? reconcile().highOrCriticalFindings() : null;
        List<OperationsAlertDto> alerts = new ArrayList<>();
        thresholds.forEach((condition, threshold) -> {
            long value = switch (condition) {
                case RUNS_NEEDING_INTERVENTION -> summary.automaticBillingRuns().needingIntervention();
                case RUNS_STUCK -> summary.automaticBillingRuns().stuck();
                case RUNS_RETRY_SCHEDULED -> summary.automaticBillingRuns().retryScheduled();
                case OCCURRENCES_OVERDUE -> summary.occurrences().overdueWithoutRun();
                case INVOICES_PENDING_APPROVAL -> summary.invoices().awaitingApproval();
                case DOCUMENTS_MISSING -> summary.invoices().approvedWithoutDocument();
                case DELIVERIES_FAILED -> summary.invoices().deliveryFailedNotSent();
                case SENT_WITHOUT_RECEIVABLE -> summary.receivables().sentWithoutReceivable();
                case RECONCILIATION_EXCEPTIONS -> Objects.requireNonNull(reconciliationExceptions);
            };
            alerts.add(new OperationsAlertDto(condition, value, threshold, value > threshold));
        });
        return alerts;
    }

    // ================================================================ helpers

    private CheckResult collect(ReconciliationCheck check, int limit, Consumer<Consumer<Object[]>> query,
            Function<Object[], Finding> mapper) {
        CheckCollector collector = new CheckCollector(check, limit);
        query.accept(row -> collector.add(mapper.apply(row)));
        return collector.result();
    }

    /** Rows starting [invoiceId, invoiceNumber, ...]. */
    private static Finding invoiceFinding(Object[] row, UUID relatedId, String expected, String actual,
            String detail) {
        return invoiceFinding(row, relatedId, expected, actual, detail, null);
    }

    private static Finding invoiceFinding(Object[] row, UUID relatedId, String expected, String actual,
            String detail, Severity severity) {
        return new Finding(RecordType.INVOICE, (UUID) row[0], text(row[1]), relatedId, expected, actual, severity,
                detail);
    }

    /** Rows [invoiceId, invoiceNumber, status]. */
    private static Finding statusFinding(Object[] row, String expected) {
        return new Finding(RecordType.INVOICE, (UUID) row[0], text(row[1]), null, expected, "status " + text(row[2]),
                null, null);
    }

    /** Rows [receivableId, invoiceNumber, invoiceId, ...]. */
    private static Finding receivableFinding(Object[] row, String expected, String actual, String detail) {
        return new Finding(RecordType.RECEIVABLE, (UUID) row[0], text(row[1]), (UUID) row[2], expected, actual, null,
                detail);
    }

    /**
     * A difference of at most one paisa per line is the known rounding
     * pattern of records created before the UAT-D2 fix (lines rounded
     * separately from the subtotal) - MEDIUM; anything larger keeps the
     * check's severity.
     */
    private static Severity lineSeverity(ReconciliationCheck check, Object expected, Object actual, Object lineCount) {
        long lines = toLong(lineCount);
        if (expected instanceof BigDecimal total && actual instanceof BigDecimal sum && lines > 0) {
            BigDecimal tolerance = LINE_ROUNDING_TOLERANCE_PER_ITEM.multiply(BigDecimal.valueOf(lines));
            if (total.subtract(sum).abs().compareTo(tolerance) <= 0) {
                return Severity.MEDIUM;
            }
        }
        return check.getSeverity();
    }

    private static Map<String, Long> countsByKey(List<Object[]> rows) {
        Map<String, Long> counts = new TreeMap<>();
        for (Object[] row : rows) {
            counts.merge(row[0] == null ? "UNKNOWN" : name(row[0]), toLong(row[1]), Long::sum);
        }
        return counts;
    }

    private static List<TypeStatusCount> typeStatusCounts(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new TypeStatusCount(row[0] == null ? "UNKNOWN" : text(row[0]), name(row[1]),
                        toLong(row[2])))
                .sorted(Comparator.comparing(TypeStatusCount::billingType).thenComparing(TypeStatusCount::status))
                .toList();
    }

    private static String name(Object value) {
        return value instanceof Enum<?> e ? e.name() : String.valueOf(value);
    }

    private static String text(Object value) {
        if (value == null) {
            return "null";
        }
        return value instanceof BigDecimal amount ? amount.toPlainString() : name(value);
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    /** Counts every finding of one check and keeps the first {@code limit}. */
    private static final class CheckCollector {
        private final ReconciliationCheck check;
        private final int limit;
        private final List<Finding> findings = new ArrayList<>();
        private long count;
        private long highOrCriticalCount;

        private CheckCollector(ReconciliationCheck check, int limit) {
            this.check = check;
            this.limit = limit;
        }

        void add(Finding finding) {
            Severity severity = finding.severity() != null ? finding.severity() : check.getSeverity();
            count++;
            if (severity.compareTo(Severity.HIGH) >= 0) {
                highOrCriticalCount++;
            }
            if (findings.size() < limit) {
                findings.add(new Finding(finding.recordType(), finding.recordId(), finding.reference(),
                        finding.relatedId(), finding.expected(), finding.actual(), severity, finding.detail()));
            }
        }

        CheckResult result() {
            return new CheckResult(check, check.getSeverity(), check.getOwner(), check.getBusinessImpact(),
                    check.getCorrectiveAction(), count, highOrCriticalCount, count > findings.size(),
                    List.copyOf(findings));
        }
    }
}
