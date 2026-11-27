package com.AccountReceivableManagement.dto.billing_data_acquisition;

/** Whether a billing period can be acquired as a new batch now, as reported by period discovery. */
public enum BillingPeriodReadiness {
    /** Newly eligible, unclaimed records exist and all of them pass rate and eligibility validation. */
    READY,
    /** Nothing new: every approved record of the period is already billed (or there is none). */
    NO_NEW_ITEMS,
    /** New records exist but acquisition would fail; see {@code blockingReasons}. */
    BLOCKED,
    /** TMS or XMS could not be read, so what is new cannot be determined; see {@code blockingReasons}. */
    SOURCE_UNAVAILABLE
}
