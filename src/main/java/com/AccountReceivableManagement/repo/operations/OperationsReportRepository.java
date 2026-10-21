package com.AccountReceivableManagement.repo.operations;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType;
import com.AccountReceivableManagement.entity_enums.receivable.ReceivableStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Read-only aggregate and reconciliation queries over the billing chain for
 * operations reporting. Every query is a SELECT; aggregation happens in the
 * database and only scalars are returned - no entity is loaded, so nothing
 * here can be flushed back. Joins between records linked by plain id
 * columns (invoice → occurrence/snapshot/tax calculation, run → invoice) use
 * entity joins on those ids, served by the existing unique keys/indexes.
 */
@Repository
public class OperationsReportRepository {

    private static final EnumSet<AutomaticBillingRunStatus> RUN_DONE =
            EnumSet.of(AutomaticBillingRunStatus.PENDING_APPROVAL, AutomaticBillingRunStatus.COMPLETED);
    private static final EnumSet<InvoiceStatus> APPROVED_STATUSES =
            EnumSet.of(InvoiceStatus.APPROVED, InvoiceStatus.READY_TO_SEND);

    /** Same filter as BillingScheduleRepository.countDueOccurrencesWithoutAutomaticRun. */
    private static final String DUE_AUTOMATIC_OCCURRENCE_WITHOUT_RUN =
            "FROM BillingSchedule s JOIN s.billingConfiguration c "
                    + "WHERE c.approvalStatus = :approved AND c.invoiceGenerationType = :automatic "
                    + "AND c.manuallyDeactivated = false "
                    + "AND s.isActive = true AND s.isInvoiced = false "
                    + "AND s.billingDate <= :asOf AND s.periodStatus IN :unbilled "
                    + "AND NOT EXISTS (SELECT r.automaticBillingRunId FROM AutomaticBillingRun r "
                    + "                WHERE r.billingScheduleId = s.billingScheduleId)";

    private static final String SENT_DELIVERY_EXISTS =
            "EXISTS (SELECT d.invoiceDeliveryId FROM InvoiceDelivery d WHERE d.invoiceId = i.invoiceId AND d.status = :sent)";

    private static final String DOCUMENT_EXISTS =
            "EXISTS (SELECT doc.invoiceDocumentId FROM InvoiceDocument doc WHERE doc.invoiceId = i.invoiceId)";

    @PersistenceContext
    private EntityManager entityManager;

    // ================================================================ summary: runs (execution status)

    public List<Object[]> runCountsByStatus() {
        return rows("SELECT r.status, COUNT(r) FROM AutomaticBillingRun r GROUP BY r.status", Map.of());
    }

    public List<Object[]> runCountsByBillingTypeAndStatus() {
        return rows("SELECT bt.billingTypeName, r.status, COUNT(r) FROM AutomaticBillingRun r "
                + "JOIN r.billingConfiguration c LEFT JOIN c.billingType bt "
                + "GROUP BY bt.billingTypeName, r.status", Map.of());
    }

    public List<Object[]> runCountsByCreatedDateAndStatus(LocalDateTime from) {
        return rows("SELECT CAST(r.createdAt AS LocalDate), r.status, COUNT(r) FROM AutomaticBillingRun r "
                + "WHERE r.createdAt >= :from "
                + "GROUP BY CAST(r.createdAt AS LocalDate), r.status", Map.of("from", from));
    }

    /** Current FAILED runs by failure step; {@code retryScheduled} selects waiting-for-retry vs needing a person. */
    public List<Object[]> failedRunsByStep(boolean retryScheduled) {
        return rows("SELECT r.failureStep, COUNT(r) FROM AutomaticBillingRun r WHERE r.status = :failed "
                + "AND r.nextRetryAt IS " + (retryScheduled ? "NOT NULL" : "NULL") + " GROUP BY r.failureStep",
                Map.of("failed", AutomaticBillingRunStatus.FAILED));
    }

    public long countRetriesExhausted(int maxAttempts) {
        return count("SELECT COUNT(r) FROM AutomaticBillingRun r WHERE r.status = :failed "
                + "AND r.nextRetryAt IS NULL AND r.attemptCount >= :maxAttempts",
                Map.of("failed", AutomaticBillingRunStatus.FAILED, "maxAttempts", maxAttempts));
    }

    public long countRetriedRuns() {
        return count("SELECT COUNT(r) FROM AutomaticBillingRun r WHERE r.attemptCount > 1", Map.of());
    }

    public long countRecoveredRuns() {
        return count("SELECT COUNT(r) FROM AutomaticBillingRun r WHERE r.status IN :done "
                + "AND r.failureStep IS NOT NULL", Map.of("done", RUN_DONE));
    }

    // ================================================================ summary: occurrences

    public long countDueOccurrencesWithoutRun(LocalDate asOf) {
        return count("SELECT COUNT(s) " + DUE_AUTOMATIC_OCCURRENCE_WITHOUT_RUN, dueParameters(asOf));
    }

    public LocalDate oldestDueOccurrenceWithoutRun(LocalDate asOf) {
        Object value = query("SELECT MIN(s.billingDate) " + DUE_AUTOMATIC_OCCURRENCE_WITHOUT_RUN,
                dueParameters(asOf)).getSingleResult();
        return (LocalDate) value;
    }

    // ================================================================ summary: invoices (business status)

    public List<Object[]> invoiceCountsByStatus() {
        return rows("SELECT i.status, COUNT(i) FROM Invoice i GROUP BY i.status", Map.of());
    }

    /**
     * Billing type of an invoice: its occurrence's configuration type, or -
     * for snapshot-based (T&amp;M) invoices - the type frozen on the snapshot.
     */
    public List<Object[]> invoiceCountsByBillingTypeAndStatus() {
        return rows("SELECT COALESCE(bt.billingTypeName, sn.billingType), i.status, COUNT(i) FROM Invoice i "
                + "LEFT JOIN BillingSchedule s ON s.billingScheduleId = i.billingScheduleId "
                + "LEFT JOIN s.billingConfiguration c LEFT JOIN c.billingType bt "
                + "LEFT JOIN BillingSnapshot sn ON sn.id = i.billingSnapshotId "
                + "GROUP BY COALESCE(bt.billingTypeName, sn.billingType), i.status", Map.of());
    }

    public List<Object[]> automaticInvoiceCountsByStatus() {
        return rows("SELECT i.status, COUNT(i) FROM Invoice i WHERE EXISTS (SELECT r.automaticBillingRunId "
                + "FROM AutomaticBillingRun r WHERE r.invoiceId = i.invoiceId) GROUP BY i.status", Map.of());
    }

    public long countApprovedWithoutDocument() {
        return count("SELECT COUNT(i) FROM Invoice i WHERE i.status = :approved AND NOT " + DOCUMENT_EXISTS,
                Map.of("approved", InvoiceStatus.APPROVED));
    }

    public long countReadyForDelivery() {
        return count("SELECT COUNT(i) FROM Invoice i WHERE i.status = :ready AND NOT " + SENT_DELIVERY_EXISTS,
                Map.of("ready", InvoiceStatus.READY_TO_SEND, "sent", InvoiceDeliveryStatus.SENT));
    }

    public long countSentInvoices() {
        return count("SELECT COUNT(DISTINCT d.invoiceId) FROM InvoiceDelivery d WHERE d.status = :sent",
                Map.of("sent", InvoiceDeliveryStatus.SENT));
    }

    public long countDeliveryFailedNotSent() {
        return count("SELECT COUNT(DISTINCT d.invoiceId) FROM InvoiceDelivery d WHERE d.status = :failed "
                + "AND NOT EXISTS (SELECT d2.invoiceDeliveryId FROM InvoiceDelivery d2 "
                + "                WHERE d2.invoiceId = d.invoiceId AND d2.status = :sent)",
                Map.of("failed", InvoiceDeliveryStatus.FAILED, "sent", InvoiceDeliveryStatus.SENT));
    }

    public long countFailedDeliveryAttempts() {
        return count("SELECT COUNT(d) FROM InvoiceDelivery d WHERE d.status = :failed",
                Map.of("failed", InvoiceDeliveryStatus.FAILED));
    }

    // ================================================================ summary: receivables

    public List<Object[]> receivableCountsByStatus() {
        return rows("SELECT r.status, COUNT(r) FROM Receivable r GROUP BY r.status", Map.of());
    }

    /**
     * Deliveries whose outcome is unknown: UNCERTAIN, or PENDING since before
     * {@code pendingBefore} (no longer in flight). They block resending until
     * an operator resolves them (G-19/G-20).
     */
    public long countDeliveriesWithUnknownOutcome(LocalDateTime pendingBefore) {
        return count("SELECT COUNT(d) FROM InvoiceDelivery d WHERE d.status = :uncertain "
                        + "OR (d.status = :pending AND d.createdAt < :pendingBefore)",
                Map.of("uncertain", InvoiceDeliveryStatus.UNCERTAIN, "pending", InvoiceDeliveryStatus.PENDING,
                        "pendingBefore", pendingBefore));
    }

    /**
     * PENDING_APPROVAL runs whose invoice is already approved - completion
     * after approval was not recorded; the scheduler reconciles them (G-18).
     */
    public long countRunsAwaitingCompletionOfApprovedInvoice() {
        return count("SELECT COUNT(r) FROM AutomaticBillingRun r, "
                        + "com.AccountReceivableManagement.entity.invoice_generation.Invoice i "
                        + "WHERE r.status = :pendingApproval AND i.invoiceId = r.invoiceId AND i.status IN :approved",
                Map.of("pendingApproval", AutomaticBillingRunStatus.PENDING_APPROVAL, "approved", APPROVED_STATUSES));
    }

    public long countSentInvoices(boolean withReceivable) {
        return count("SELECT COUNT(DISTINCT d.invoiceId) FROM InvoiceDelivery d WHERE d.status = :sent AND "
                + (withReceivable ? "" : "NOT ")
                + "EXISTS (SELECT rc.receivableId FROM Receivable rc WHERE rc.invoice.invoiceId = d.invoiceId)",
                Map.of("sent", InvoiceDeliveryStatus.SENT));
    }

    // ================================================================ reconciliation
    //
    // Each method streams the discrepancy rows of one check to the consumer
    // (scalars only), so the caller can count all of them and keep a bounded
    // sample. No row means the check passed.

    /** [invoiceId, invoiceNumber, occurrenceId, occurrenceExists, occurrenceAmount, subtotal] */
    public void occurrenceAmountDiscrepancies(Consumer<Object[]> sink) {
        stream("SELECT i.invoiceId, i.invoiceNumber, i.billingScheduleId, s.billingScheduleId, s.billingAmount, "
                + "i.subtotal FROM Invoice i LEFT JOIN BillingSchedule s ON s.billingScheduleId = i.billingScheduleId "
                + "WHERE i.billingScheduleId IS NOT NULL AND (s.billingScheduleId IS NULL OR s.billingAmount IS NULL "
                + "OR i.subtotal IS NULL OR s.billingAmount <> i.subtotal)", Map.of(), sink);
    }

    /** Snapshot-based invoices: [invoiceId, invoiceNumber, snapshotId, snapshotExists, snapshotTotal, subtotal] */
    public void snapshotAmountDiscrepancies(Consumer<Object[]> sink) {
        stream("SELECT i.invoiceId, i.invoiceNumber, i.billingSnapshotId, sn.id, sn.totalAmount, i.subtotal "
                + "FROM Invoice i LEFT JOIN BillingSnapshot sn ON sn.id = i.billingSnapshotId "
                + "WHERE i.billingScheduleId IS NULL AND (i.billingSnapshotId IS NULL OR sn.id IS NULL "
                + "OR sn.totalAmount IS NULL OR i.subtotal IS NULL OR sn.totalAmount <> i.subtotal)", Map.of(), sink);
    }

    /** Invoiced snapshots: [snapshotId, snapshotNumber, totalAmount, Σ item amounts, item count] */
    public void snapshotItemDiscrepancies(Consumer<Object[]> sink) {
        stream("SELECT sn.id, sn.snapshotNumber, sn.totalAmount, COALESCE(SUM(it.amount), 0), COUNT(it) "
                + "FROM BillingSnapshot sn LEFT JOIN sn.items it "
                + "WHERE EXISTS (SELECT i.invoiceId FROM Invoice i WHERE i.billingSnapshotId = sn.id "
                + "              AND i.billingScheduleId IS NULL) "
                + "GROUP BY sn.id, sn.snapshotNumber, sn.totalAmount "
                + "HAVING sn.totalAmount IS NULL OR COALESCE(SUM(it.amount), 0) <> sn.totalAmount", Map.of(), sink);
    }

    /** [invoiceId, invoiceNumber, subtotal, Σ line amounts, line count] */
    public void invoiceLineDiscrepancies(Consumer<Object[]> sink) {
        stream("SELECT i.invoiceId, i.invoiceNumber, i.subtotal, COALESCE(SUM(it.amount), 0), COUNT(it) "
                + "FROM Invoice i LEFT JOIN i.items it WHERE i.subtotal IS NOT NULL "
                + "GROUP BY i.invoiceId, i.invoiceNumber, i.subtotal "
                + "HAVING COUNT(it) = 0 OR COALESCE(SUM(it.amount), 0) <> i.subtotal", Map.of(), sink);
    }

    /** [invoiceId, invoiceNumber, subtotal, totalTax, grandTotal] */
    public void invoiceTotalDiscrepancies(Consumer<Object[]> sink) {
        stream("SELECT i.invoiceId, i.invoiceNumber, i.subtotal, i.totalTaxAmount, i.grandTotal FROM Invoice i "
                + "WHERE i.subtotal IS NULL OR i.totalTaxAmount IS NULL OR i.grandTotal IS NULL "
                + "OR i.subtotal + i.totalTaxAmount <> i.grandTotal", Map.of(), sink);
    }

    /** [invoiceId, invoiceNumber, totalTax, Σ tax components] */
    public void invoiceTaxBreakdownDiscrepancies(Consumer<Object[]> sink) {
        stream("SELECT i.invoiceId, i.invoiceNumber, i.totalTaxAmount, COALESCE(SUM(tc.taxAmount), 0) "
                + "FROM Invoice i LEFT JOIN i.taxComponents tc WHERE i.totalTaxAmount IS NOT NULL "
                + "GROUP BY i.invoiceId, i.invoiceNumber, i.totalTaxAmount "
                + "HAVING COALESCE(SUM(tc.taxAmount), 0) <> i.totalTaxAmount", Map.of(), sink);
    }

    /**
     * [invoiceId, invoiceNumber, taxCalculationId, taxCalculationExists, subtotal, totalTax, grandTotal,
     * taxable, calculatedTax, calculatedGrandTotal]
     */
    public void invoiceTaxCalculationDiscrepancies(Consumer<Object[]> sink) {
        stream("SELECT i.invoiceId, i.invoiceNumber, i.taxCalculationId, t.taxCalculationId, i.subtotal, "
                + "i.totalTaxAmount, i.grandTotal, t.taxableAmount, t.totalTaxAmount, t.grandTotal FROM Invoice i "
                + "LEFT JOIN TaxCalculation t ON t.taxCalculationId = i.taxCalculationId "
                + "WHERE t.taxCalculationId IS NULL OR t.taxableAmount <> i.subtotal "
                + "OR t.totalTaxAmount <> i.totalTaxAmount OR t.grandTotal <> i.grandTotal", Map.of(), sink);
    }

    /** [occurrenceId, invoice count] */
    public void duplicateInvoicesPerOccurrence(Consumer<Object[]> sink) {
        stream("SELECT i.billingScheduleId, COUNT(i) FROM Invoice i WHERE i.billingScheduleId IS NOT NULL "
                + "GROUP BY i.billingScheduleId HAVING COUNT(i) > 1", Map.of(), sink);
    }

    /** [snapshotId, invoice count] - snapshot-based invoices only. */
    public void duplicateInvoicesPerSnapshot(Consumer<Object[]> sink) {
        stream("SELECT i.billingSnapshotId, COUNT(i) FROM Invoice i WHERE i.billingScheduleId IS NULL "
                + "AND i.billingSnapshotId IS NOT NULL GROUP BY i.billingSnapshotId HAVING COUNT(i) > 1", Map.of(), sink);
    }

    /** [occurrenceId, billingDate] */
    public void occurrencesInvoicedWithoutInvoice(Consumer<Object[]> sink) {
        stream("SELECT s.billingScheduleId, s.billingDate FROM BillingSchedule s "
                + "WHERE (s.isInvoiced = true OR s.periodStatus = :invoiced) "
                + "AND NOT EXISTS (SELECT i.invoiceId FROM Invoice i WHERE i.billingScheduleId = s.billingScheduleId)",
                Map.of("invoiced", BillingPeriodStatus.INVOICED), sink);
    }

    /** [invoiceId, invoiceNumber, occurrenceId, periodStatus] */
    public void invoicesWithOccurrenceNotMarkedInvoiced(Consumer<Object[]> sink) {
        stream("SELECT i.invoiceId, i.invoiceNumber, s.billingScheduleId, s.periodStatus FROM Invoice i "
                + "JOIN BillingSchedule s ON s.billingScheduleId = i.billingScheduleId "
                + "WHERE s.isInvoiced = false OR s.isInvoiced IS NULL", Map.of(), sink);
    }

    /** [runId, status, invoiceId (may be null)] */
    public void runsWithoutInvoice(Consumer<Object[]> sink) {
        stream("SELECT r.automaticBillingRunId, r.status, r.invoiceId FROM AutomaticBillingRun r "
                + "WHERE r.status IN :done AND (r.invoiceId IS NULL OR NOT EXISTS "
                + "(SELECT i.invoiceId FROM Invoice i WHERE i.invoiceId = r.invoiceId))", Map.of("done", RUN_DONE), sink);
    }

    /** [runId, run occurrenceId, invoiceId, invoiceNumber, invoice occurrenceId] */
    public void runInvoiceOccurrenceMismatches(Consumer<Object[]> sink) {
        stream("SELECT r.automaticBillingRunId, r.billingScheduleId, i.invoiceId, i.invoiceNumber, i.billingScheduleId "
                + "FROM AutomaticBillingRun r JOIN Invoice i ON i.invoiceId = r.invoiceId "
                + "WHERE r.billingScheduleId IS NOT NULL "
                + "AND (i.billingScheduleId IS NULL OR i.billingScheduleId <> r.billingScheduleId)", Map.of(), sink);
    }

    /** [invoiceId, run count] */
    public void invoicesWithMultipleRuns(Consumer<Object[]> sink) {
        stream("SELECT r.invoiceId, COUNT(r) FROM AutomaticBillingRun r WHERE r.invoiceId IS NOT NULL "
                + "GROUP BY r.invoiceId HAVING COUNT(r) > 1", Map.of(), sink);
    }

    /** [occurrenceId, billingDate, configurationId] - oldest first. */
    public void overdueOccurrencesWithoutRun(LocalDate asOf, Consumer<Object[]> sink) {
        stream("SELECT s.billingScheduleId, s.billingDate, c.billingConfigurationId "
                + DUE_AUTOMATIC_OCCURRENCE_WITHOUT_RUN + " ORDER BY s.billingDate", dueParameters(asOf), sink);
    }

    /** [invoiceId, invoiceNumber, status] */
    public void readyToSendWithoutDocument(Consumer<Object[]> sink) {
        stream("SELECT i.invoiceId, i.invoiceNumber, i.status FROM Invoice i WHERE i.status = :ready AND NOT "
                + DOCUMENT_EXISTS, Map.of("ready", InvoiceStatus.READY_TO_SEND), sink);
    }

    /** [invoiceId, invoiceNumber, status] */
    public void approvedWithoutDocument(Consumer<Object[]> sink) {
        stream("SELECT i.invoiceId, i.invoiceNumber, i.status FROM Invoice i WHERE i.status = :approved AND NOT "
                + DOCUMENT_EXISTS, Map.of("approved", InvoiceStatus.APPROVED), sink);
    }

    /** [invoiceId, invoiceNumber, status] */
    public void documentsForUnapprovedInvoices(Consumer<Object[]> sink) {
        stream("SELECT i.invoiceId, i.invoiceNumber, i.status FROM Invoice i WHERE i.status NOT IN :approved AND "
                + DOCUMENT_EXISTS, Map.of("approved", APPROVED_STATUSES), sink);
    }

    /** [invoiceId, invoiceNumber, status] */
    public void sentInvoicesNotApproved(Consumer<Object[]> sink) {
        stream("SELECT i.invoiceId, i.invoiceNumber, i.status FROM Invoice i WHERE i.status NOT IN :approved AND "
                + SENT_DELIVERY_EXISTS,
                Map.of("approved", APPROVED_STATUSES, "sent", InvoiceDeliveryStatus.SENT), sink);
    }

    /** [invoiceId, invoiceNumber, grandTotal] */
    public void sentInvoicesWithoutReceivable(Consumer<Object[]> sink) {
        stream("SELECT i.invoiceId, i.invoiceNumber, i.grandTotal FROM Invoice i WHERE " + SENT_DELIVERY_EXISTS
                + " AND NOT EXISTS (SELECT rc.receivableId FROM Receivable rc WHERE rc.invoice.invoiceId = i.invoiceId)",
                Map.of("sent", InvoiceDeliveryStatus.SENT), sink);
    }

    /** [receivableId, invoiceNumber, invoiceId] */
    public void receivablesWithoutSentDelivery(Consumer<Object[]> sink) {
        stream("SELECT rc.receivableId, rc.invoiceNumber, i.invoiceId FROM Receivable rc JOIN rc.invoice i "
                + "WHERE NOT " + SENT_DELIVERY_EXISTS, Map.of("sent", InvoiceDeliveryStatus.SENT), sink);
    }

    /** [receivableId, invoiceNumber, invoiceId, grandTotal, invoiceAmount] */
    public void receivableAmountMismatches(Consumer<Object[]> sink) {
        stream("SELECT rc.receivableId, rc.invoiceNumber, i.invoiceId, i.grandTotal, rc.invoiceAmount "
                + "FROM Receivable rc JOIN rc.invoice i WHERE i.grandTotal IS NULL OR rc.invoiceAmount <> i.grandTotal",
                Map.of(), sink);
    }

    /**
     * No process reduces a balance yet (ReceivableStatus), so an OUTSTANDING
     * balance different from the invoice amount, or any balance above it or
     * below zero, is unexpected.
     * [receivableId, invoiceNumber, invoiceId, invoiceAmount, outstandingBalance, status]
     */
    public void unexpectedReceivableBalances(Consumer<Object[]> sink) {
        stream("SELECT rc.receivableId, rc.invoiceNumber, rc.invoice.invoiceId, rc.invoiceAmount, "
                + "rc.outstandingBalance, rc.status FROM Receivable rc "
                + "WHERE rc.outstandingBalance > rc.invoiceAmount OR rc.outstandingBalance < 0 "
                + "OR (rc.status = :outstanding AND rc.outstandingBalance <> rc.invoiceAmount)",
                Map.of("outstanding", ReceivableStatus.OUTSTANDING), sink);
    }

    /** [invoiceId, receivable count] */
    public void duplicateReceivables(Consumer<Object[]> sink) {
        stream("SELECT rc.invoice.invoiceId, COUNT(rc) FROM Receivable rc GROUP BY rc.invoice.invoiceId "
                + "HAVING COUNT(rc) > 1", Map.of(), sink);
    }

    // ================================================================ plumbing

    private static Map<String, Object> dueParameters(LocalDate asOf) {
        return Map.of("approved", ApprovalStatus.APPROVED, "automatic", InvoiceGenerationType.AUTOMATIC,
                "asOf", asOf, "unbilled", BillingSchedule.UNBILLED_STATUSES);
    }

    private Query query(String jpql, Map<String, Object> parameters) {
        Query query = entityManager.createQuery(jpql);
        parameters.forEach(query::setParameter);
        return query;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String jpql, Map<String, Object> parameters) {
        return query(jpql, parameters).getResultList();
    }

    private long count(String jpql, Map<String, Object> parameters) {
        return ((Number) query(jpql, parameters).getSingleResult()).longValue();
    }

    @SuppressWarnings("unchecked")
    private void stream(String jpql, Map<String, Object> parameters, Consumer<Object[]> sink) {
        try (Stream<Object[]> results = query(jpql, parameters).getResultStream()) {
            results.forEach(sink);
        }
    }
}
