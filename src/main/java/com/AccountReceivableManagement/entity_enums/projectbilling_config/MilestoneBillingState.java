package com.AccountReceivableManagement.entity_enums.projectbilling_config;

/**
 * Where a PMS milestone stands in AR billing. Derived on read from the payment entry that claims the milestone and the
 * billing occurrence generated for it - never stored, so it cannot drift from those records.
 */
public enum MilestoneBillingState {

    /** No live billing plan holds the milestone. */
    AVAILABLE,

    /** A plan holds it (draft, pending, approved or rejected) but billing has not started: no occurrence yet, or still SCHEDULED. */
    RESERVED,

    /** Its occurrence is in tax processing (TAX_PENDING / TAX_CALCULATED); billing has started and the milestone is frozen. */
    IN_BILLING,

    /** An invoice has been generated for its occurrence (whatever the invoice's approval status); it is billed for good. */
    INVOICED
}
