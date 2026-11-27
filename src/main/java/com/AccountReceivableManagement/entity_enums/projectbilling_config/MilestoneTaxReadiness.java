package com.AccountReceivableManagement.entity_enums.projectbilling_config;

/**
 * Whether a PMS milestone may be handed to tax calculation now. Derived on read from the synchronised
 * {@code execution_status} and {@code planned_due_date} of {@code project_milestone_reference} - never stored, so it
 * cannot drift from what CDC last delivered from PMS.
 *
 * <p>This is a second gate on top of the PMS verdict ({@code billable} + {@code billing_readiness}, see
 * {@code BillingMilestonePlanServiceImpl.ineligibilityReason}): a milestone must satisfy both before its occurrence
 * leaves SCHEDULED.</p>
 */
public enum MilestoneTaxReadiness {

    /** Completed in PMS and its planned due date is today or past: the occurrence may move to TAX_PENDING. */
    READY_FOR_TAX,

    /** Completed in PMS but its planned due date is still in the future: the occurrence waits. */
    UPCOMING,

    /** Not completed in PMS yet; the planned due date is not looked at. */
    NOT_COMPLETED
}
