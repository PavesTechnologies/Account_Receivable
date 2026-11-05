package com.AccountReceivableManagement.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Headline figures. Balances are as of the as-of date; invoiced figures cover the period windows. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardKpisDto {

    /** Open receivables (sent invoices with a balance). */
    private MoneyMetricDto outstanding;
    /** Open receivables past their due date. */
    private MoneyMetricDto overdue;
    /** Overdue by more than criticalOverdueDays. */
    private MoneyMetricDto criticalOverdue;
    private int criticalOverdueDays;
    /** Billable work captured but not yet invoiced (pre-invoice snapshots and due schedules). */
    private MoneyMetricDto unbilledWip;
    private long unbilledProjectCount;
    /** Invoices waiting for checker approval. */
    private MoneyMetricDto pendingApproval;
    private MoneyMetricDto invoicedInPeriod;
    private MoneyMetricDto invoicedPriorPeriod;
    private CollectionsDto collections;
}
