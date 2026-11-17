package com.AccountReceivableManagement.entity_enums.automatic_billing;

import java.util.EnumSet;
import java.util.Set;

/**
 * Automation lifecycle of one {@code AutomaticBillingRun} - deliberately
 * separate from the Billing Occurrence's own {@code BillingPeriodStatus},
 * which keeps describing the billing event itself. Which pipeline step a
 * PROCESSING or FAILED run is at is recorded separately as an
 * {@link AutomaticBillingStep}.
 * <pre>
 * SCHEDULED ──claim──▶ PROCESSING ──▶ PENDING_APPROVAL ──▶ COMPLETED
 *                        │   ▲  │
 *                        │   │  └──────────────────────────▶ COMPLETED
 *                        ▼   │claim
 *                       FAILED
 * </pre>
 * PROCESSING → PROCESSING is a re-claim of a run whose worker's claim
 * expired (crashed or stalled worker).
 */
public enum AutomaticBillingRunStatus {

    /** Created for a due billing event; not yet picked up by a worker. */
    SCHEDULED,

    /** Claimed by exactly one worker, which is executing its steps. */
    PROCESSING,

    /** The last attempt failed at {@code failureStep}; can be claimed again. */
    FAILED,

    /**
     * Automation has created the invoice and submitted it for the existing
     * Finance Manager approval - its part is done until approval.
     */
    PENDING_APPROVAL,

    /** Terminal: nothing further for automation to do. */
    COMPLETED;

    /** Statuses a worker may claim (PROCESSING only once its claim has expired). */
    public static final Set<AutomaticBillingRunStatus> CLAIMABLE = EnumSet.of(SCHEDULED, FAILED);

    public boolean canTransitionTo(AutomaticBillingRunStatus target) {
        return switch (this) {
            case SCHEDULED -> target == PROCESSING;
            case PROCESSING -> target == PROCESSING || target == FAILED
                    || target == PENDING_APPROVAL || target == COMPLETED;
            case FAILED -> target == PROCESSING;
            case PENDING_APPROVAL -> target == COMPLETED;
            case COMPLETED -> false;
        };
    }
}
