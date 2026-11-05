package com.AccountReceivableManagement.entity_enums.operations;

/**
 * Conditions that can raise an operations alert. Each is a count read from
 * persisted records (run, invoice, delivery, receivable) at evaluation time;
 * the alert is raised when the count exceeds the configured threshold
 * ({@code ar.operations.alerts.thresholds.<CONDITION>}).
 */
public enum OperationsAlertCondition {

    /** FAILED runs with no automatic retry left (data error or retries exhausted). */
    RUNS_NEEDING_INTERVENTION,

    /** PROCESSING runs whose claim expired - the scheduler is not recovering them. */
    RUNS_STUCK,

    /** FAILED runs waiting for a backed-off retry - a dependency may be failing. */
    RUNS_RETRY_SCHEDULED,

    /** Due occurrences of automatic configurations past the grace period with no run. */
    OCCURRENCES_OVERDUE,

    /** Invoices awaiting Finance Manager approval (business backlog). */
    INVOICES_PENDING_APPROVAL,

    /** APPROVED invoices without a finalized document (document generation failed). */
    DOCUMENTS_MISSING,

    /** Invoices whose delivery attempts failed and none succeeded. */
    DELIVERIES_FAILED,

    /** Invoices with a SENT delivery but no receivable. */
    SENT_WITHOUT_RECEIVABLE,

    /**
     * Deliveries whose outcome is unknown (UNCERTAIN, or PENDING past the
     * in-flight timeout) - each blocks its invoice until resolved.
     */
    DELIVERIES_UNKNOWN_OUTCOME,

    /** PENDING_APPROVAL runs whose invoice is already approved and that reconciliation has not completed. */
    RUNS_APPROVED_NOT_COMPLETED,

    /** Reconciliation findings of HIGH or CRITICAL severity. */
    RECONCILIATION_EXCEPTIONS
}
