package com.AccountReceivableManagement.repo.dashboard;

import com.AccountReceivableManagement.dto.dashboard.DashboardInvoiceView;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceApprovalAction;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.repo.dashboard.rows.ApprovalEventRow;
import com.AccountReceivableManagement.repo.dashboard.rows.BillingTypeTotalRow;
import com.AccountReceivableManagement.repo.dashboard.rows.ConfigEventRow;
import com.AccountReceivableManagement.repo.dashboard.rows.ConfigStatusCountRow;
import com.AccountReceivableManagement.repo.dashboard.rows.CurrencyTotalRow;
import com.AccountReceivableManagement.repo.dashboard.rows.DeliveryEventRow;
import com.AccountReceivableManagement.repo.dashboard.rows.InvoiceStatusTotalRow;
import com.AccountReceivableManagement.repo.dashboard.rows.LedgerRow;
import com.AccountReceivableManagement.repo.dashboard.rows.MonthTotalRow;
import com.AccountReceivableManagement.repo.dashboard.rows.OpenReceivableRow;
import com.AccountReceivableManagement.repo.dashboard.rows.ScheduleTotalRow;
import com.AccountReceivableManagement.repo.dashboard.rows.SnapshotStatusTotalRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Read-only aggregate queries behind the AR dashboard. Kept apart from the
 * feature repositories so the dashboard never changes their contracts.
 *
 * Aggregation happens in the database (GROUP BY status / currency / month);
 * only open receivables and one ledger page are returned as individual rows.
 * Money is never summed across currencies here — every total is per currency.
 */
@Repository
public class ArDashboardQueryRepository {

    private static final String ROWS = "com.AccountReceivableManagement.repo.dashboard.rows.";

    /** Billing type as stored on the snapshot, else the schedule's configuration billing type. */
    private static final String BILLING_TYPE_JOINS = """
            left join BillingSnapshot s on s.id = i.billingSnapshotId
            left join BillingSchedule bs on bs.billingScheduleId = i.billingScheduleId
            left join bs.billingConfiguration bc
            left join bc.billingType bt
            """;
    private static final String BILLING_TYPE_EXPR = "coalesce(s.billingType, bt.billingTypeName)";

    @PersistenceContext
    private EntityManager em;

    // ---------------------------------------------------------------- receivables

    public List<OpenReceivableRow> findOpenReceivables() {
        return em.createQuery("""
                select new %sOpenReceivableRow(
                    r.invoice.invoiceId, r.invoiceNumber, r.clientName, r.currencyCode,
                    r.invoiceDate, r.dueDate, r.outstandingBalance)
                from Receivable r
                where r.outstandingBalance > 0
                """.formatted(ROWS), OpenReceivableRow.class)
                .getResultList();
    }

    // ---------------------------------------------------------------- invoices

    public List<InvoiceStatusTotalRow> sumInvoicesByStatus() {
        return em.createQuery("""
                select new %sInvoiceStatusTotalRow(i.status, i.currencyCode, sum(i.grandTotal), count(i))
                from Invoice i
                group by i.status, i.currencyCode
                """.formatted(ROWS), InvoiceStatusTotalRow.class)
                .getResultList();
    }

    /** Non-rejected invoices dated within [from, to], per currency. */
    public List<CurrencyTotalRow> sumInvoicedBetween(LocalDate from, LocalDate to) {
        return em.createQuery("""
                select new %sCurrencyTotalRow(i.currencyCode, sum(i.grandTotal), count(i))
                from Invoice i
                where i.status <> :rejected and i.invoiceDate between :from and :to
                group by i.currencyCode
                """.formatted(ROWS), CurrencyTotalRow.class)
                .setParameter("rejected", InvoiceStatus.REJECTED)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /** Non-rejected invoices dated within [from, to], per calendar month and currency. */
    public List<MonthTotalRow> sumInvoicedByMonth(LocalDate from, LocalDate to) {
        return em.createQuery("""
                select new %sMonthTotalRow(
                    year(i.invoiceDate), month(i.invoiceDate), i.currencyCode, sum(i.grandTotal), count(i))
                from Invoice i
                where i.status <> :rejected and i.invoiceDate between :from and :to
                group by year(i.invoiceDate), month(i.invoiceDate), i.currencyCode
                """.formatted(ROWS), MonthTotalRow.class)
                .setParameter("rejected", InvoiceStatus.REJECTED)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /** Non-rejected invoices dated within [from, to], per billing type and currency. */
    public List<BillingTypeTotalRow> sumInvoicedByBillingType(LocalDate from, LocalDate to) {
        return em.createQuery("""
                select new %sBillingTypeTotalRow(%s, i.currencyCode, sum(i.grandTotal), count(i))
                from Invoice i
                %s
                where i.status <> :rejected and i.invoiceDate between :from and :to
                group by %s, i.currencyCode
                """.formatted(ROWS, BILLING_TYPE_EXPR, BILLING_TYPE_JOINS, BILLING_TYPE_EXPR), BillingTypeTotalRow.class)
                .setParameter("rejected", InvoiceStatus.REJECTED)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /** For each invoice awaiting approval, when it was last submitted. */
    public List<LocalDateTime> findPendingApprovalSubmittedTimes() {
        return em.createQuery("""
                select max(h.actionAt)
                from Invoice i
                join InvoiceApprovalHistory h on h.invoiceId = i.invoiceId
                where i.status = :pending and h.action = :submitted
                group by i.invoiceId
                """, LocalDateTime.class)
                .setParameter("pending", InvoiceStatus.PENDING_APPROVAL)
                .setParameter("submitted", InvoiceApprovalAction.SUBMITTED)
                .getResultList();
    }

    /** Approved invoices that have not been sent yet (a receivable exists only after a successful send). */
    public List<CurrencyTotalRow> sumApprovedNotSent() {
        return em.createQuery("""
                select new %sCurrencyTotalRow(i.currencyCode, sum(i.grandTotal), count(i))
                from Invoice i
                where i.status = :approved
                  and not exists (select r.receivableId from Receivable r where r.invoice.invoiceId = i.invoiceId)
                group by i.currencyCode
                """.formatted(ROWS), CurrencyTotalRow.class)
                .setParameter("approved", InvoiceStatus.APPROVED)
                .getResultList();
    }

    /** Invoices whose delivery failed and that have not been sent successfully since. */
    public long countFailedDeliveriesNotSent() {
        return em.createQuery("""
                select count(distinct d.invoiceId)
                from InvoiceDelivery d
                where d.status = :failed
                  and not exists (select r.receivableId from Receivable r where r.invoice.invoiceId = d.invoiceId)
                """, Long.class)
                .setParameter("failed", InvoiceDeliveryStatus.FAILED)
                .getSingleResult();
    }

    public LocalDateTime findOldestGeneratedAt() {
        return em.createQuery("select min(i.generatedAt) from Invoice i where i.status = :generated", LocalDateTime.class)
                .setParameter("generated", InvoiceStatus.GENERATED)
                .getSingleResult();
    }

    // ---------------------------------------------------------------- billing configurations

    public List<ConfigStatusCountRow> countConfigurationsByStatus() {
        return em.createQuery("""
                select new %sConfigStatusCountRow(c.approvalStatus, c.billingStatus, count(c))
                from BillingConfiguration c
                group by c.approvalStatus, c.billingStatus
                """.formatted(ROWS), ConfigStatusCountRow.class)
                .getResultList();
    }

    /**
     * Last change time of the longest-waiting configuration in PENDING_APPROVAL.
     * There is no submitted-at column, so this is an approximation: any edit resets it.
     */
    public LocalDateTime findOldestPendingConfigurationUpdate() {
        return em.createQuery(
                        "select min(c.updatedAt) from BillingConfiguration c where c.approvalStatus = :pending",
                        LocalDateTime.class)
                .setParameter("pending", ApprovalStatus.PENDING_APPROVAL)
                .getSingleResult();
    }

    // ---------------------------------------------------------------- snapshots & schedules

    public List<SnapshotStatusTotalRow> sumSnapshotsByStatus() {
        return em.createQuery("""
                select new %sSnapshotStatusTotalRow(s.status, s.currencyCode, sum(s.totalAmount), count(s), min(s.createdDate))
                from BillingSnapshot s
                group by s.status, s.currencyCode
                """.formatted(ROWS), SnapshotStatusTotalRow.class)
                .getResultList();
    }

    /** Tax-completed snapshots with no invoice generated from them yet. */
    public List<CurrencyTotalRow> sumSnapshotsReadyForInvoice() {
        return em.createQuery("""
                select new %sCurrencyTotalRow(s.currencyCode, sum(s.totalAmount), count(s))
                from BillingSnapshot s
                where s.status = :taxCompleted
                  and not exists (select i.invoiceId from Invoice i where i.billingSnapshotId = s.id)
                group by s.currencyCode
                """.formatted(ROWS), CurrencyTotalRow.class)
                .setParameter("taxCompleted", BillingSnapshotStatus.TAX_COMPLETED)
                .getResultList();
    }

    /**
     * Schedules (milestone/recurring occurrences) that are due by {@code asOf}
     * and not yet invoiced, per tax status. Currency comes from the billing
     * configuration; schedules without one report a null currency.
     */
    public List<ScheduleTotalRow> sumDueUninvoicedSchedules(LocalDate asOf) {
        return em.createQuery("""
                select new %sScheduleTotalRow(bs.taxStatus, cur.currencyCode, sum(bs.billingAmount), count(bs), min(bs.billingDate))
                from BillingSchedule bs
                left join bs.billingConfiguration c
                left join c.currency cur
                where bs.isInvoiced = false
                  and bs.billingDate <= :asOf
                  and bs.periodStatus not in :closed
                group by bs.taxStatus, cur.currencyCode
                """.formatted(ROWS), ScheduleTotalRow.class)
                .setParameter("asOf", asOf)
                .setParameter("closed", List.of(BillingPeriodStatus.CANCELLED, BillingPeriodStatus.INVOICED))
                .getResultList();
    }

    /** Distinct PMS project ids with unbilled work (pre-invoice snapshots or due, un-invoiced schedules). */
    public Set<Long> findUnbilledProjectIds(LocalDate asOf) {
        Set<Long> ids = new LinkedHashSet<>(em.createQuery("""
                select distinct s.projectId from BillingSnapshot s
                where s.status in :statuses and s.projectId is not null
                """, Long.class)
                .setParameter("statuses", List.of(
                        BillingSnapshotStatus.READY_FOR_TAX, BillingSnapshotStatus.IN_TAX, BillingSnapshotStatus.TAX_COMPLETED))
                .getResultList());
        ids.addAll(em.createQuery("""
                select distinct p.pmsProjectId from BillingSchedule bs
                join bs.billingConfiguration c
                join c.project p
                where bs.isInvoiced = false and bs.billingDate <= :asOf and bs.periodStatus not in :closed
                """, Long.class)
                .setParameter("asOf", asOf)
                .setParameter("closed", List.of(BillingPeriodStatus.CANCELLED, BillingPeriodStatus.INVOICED))
                .getResultList());
        return ids;
    }

    // ---------------------------------------------------------------- activity

    public List<ApprovalEventRow> findRecentApprovalEvents(int limit) {
        return em.createQuery("""
                select new %sApprovalEventRow(h.action, i.invoiceNumber, h.actionBy, h.actionAt)
                from InvoiceApprovalHistory h
                left join Invoice i on i.invoiceId = h.invoiceId
                where h.actionAt is not null
                order by h.actionAt desc
                """.formatted(ROWS), ApprovalEventRow.class)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<DeliveryEventRow> findRecentDeliveryEvents(int limit) {
        return em.createQuery("""
                select new %sDeliveryEventRow(d.status, i.invoiceNumber, d.sentAt, d.failedAt, d.updatedAt)
                from InvoiceDelivery d
                left join Invoice i on i.invoiceId = d.invoiceId
                where d.status in :statuses
                order by coalesce(d.sentAt, d.failedAt, d.updatedAt) desc
                """.formatted(ROWS), DeliveryEventRow.class)
                .setParameter("statuses", List.of(
                        InvoiceDeliveryStatus.SENT, InvoiceDeliveryStatus.FAILED, InvoiceDeliveryStatus.SIMULATED))
                .setMaxResults(limit)
                .getResultList();
    }

    public List<ConfigEventRow> findRecentConfigurationEvents(int limit) {
        return em.createQuery("""
                select new %sConfigEventRow(c.approvalStatus, p.projectName, c.updatedAt)
                from BillingConfiguration c
                left join c.project p
                where c.updatedAt is not null
                order by c.updatedAt desc
                """.formatted(ROWS), ConfigEventRow.class)
                .setMaxResults(limit)
                .getResultList();
    }

    // ---------------------------------------------------------------- ledger

    /**
     * One page of the dashboard ledger, soonest due first (undated last).
     *
     * @param searchPattern lower-cased {@code %term%} pattern, or null for no search
     */
    public List<LedgerRow> findLedger(DashboardInvoiceView view, String searchPattern, LocalDate asOf, int limit) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        switch (view) {
            case OVERDUE -> where.append(" and r.receivableId is not null and r.outstandingBalance > 0 and r.dueDate < :asOf");
            case PENDING_APPROVAL -> where.append(" and i.status = :pendingApproval");
            case AWAITING_SEND -> where.append(" and i.status = :approved and r.receivableId is null");
            case SENT -> where.append(" and r.receivableId is not null");
            case ALL -> { }
        }
        if (searchPattern != null) {
            where.append("""
                     and (lower(i.invoiceNumber) like :q or lower(i.clientName) like :q or lower(i.projectName) like :q)""");
        }

        TypedQuery<LedgerRow> query = em.createQuery("""
                select new %sLedgerRow(
                    i.invoiceId, i.invoiceNumber, i.clientName, i.projectName, %s,
                    i.invoiceDate, i.dueDate, i.currencyCode, i.subtotal, i.totalTaxAmount, i.grandTotal, i.status,
                    r.receivableId, r.outstandingBalance, r.dueDate)
                from Invoice i
                left join Receivable r on r.invoice.invoiceId = i.invoiceId
                %s
                %s
                order by case when i.dueDate is null then 1 else 0 end, i.dueDate, i.invoiceNumber
                """.formatted(ROWS, BILLING_TYPE_EXPR, BILLING_TYPE_JOINS, where), LedgerRow.class);

        if (view == DashboardInvoiceView.OVERDUE) query.setParameter("asOf", asOf);
        if (view == DashboardInvoiceView.PENDING_APPROVAL) query.setParameter("pendingApproval", InvoiceStatus.PENDING_APPROVAL);
        if (view == DashboardInvoiceView.AWAITING_SEND) query.setParameter("approved", InvoiceStatus.APPROVED);
        if (searchPattern != null) query.setParameter("q", searchPattern);
        return query.setMaxResults(limit).getResultList();
    }
}
