package com.AccountReceivableManagement.entity_enums.automatic_billing;

/** What happened to an automatic billing run outside its normal processing (audit). */
public enum AutomaticBillingRunEventType {

    /** An authorized operator re-queued a FAILED (or interrupted) run. */
    REQUEUED,

    /**
     * A PENDING_APPROVAL run was completed from its invoice's persisted
     * approval because the after-commit completion did not happen.
     */
    APPROVAL_RECONCILED
}
