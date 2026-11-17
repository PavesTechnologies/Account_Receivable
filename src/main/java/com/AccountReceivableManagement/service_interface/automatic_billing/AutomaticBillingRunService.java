package com.AccountReceivableManagement.service_interface.automatic_billing;

import com.AccountReceivableManagement.entity.automatic_billing.AutomaticBillingRun;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingStep;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent state of automatic billing runs. Every method runs in its own
 * short transaction and commits before returning, so the orchestrator can
 * record progress around - never inside - external calls such as TMS.
 * Processing itself (acquisition, tax, invoice) is not done here.
 */
public interface AutomaticBillingRunService {

    /**
     * Idempotent: the run of a due Billing Occurrence of an approved,
     * AUTOMATIC configuration - the existing one if any, otherwise a new
     * SCHEDULED run. Concurrent calls for the same occurrence get the same run.
     */
    AutomaticBillingRun findOrCreateForOccurrence(UUID billingScheduleId);

    /**
     * Idempotent: the run of a due Time &amp; Material billing period, which must
     * be exactly one of the configuration's resolved billing periods.
     */
    AutomaticBillingRun findOrCreateForTimeAndMaterialPeriod(
            UUID billingConfigurationId, LocalDate billingPeriodStart, LocalDate billingPeriodEnd);

    /**
     * Starts a processing attempt: atomically moves a claimable run to
     * PROCESSING and increments its attempt count. Of concurrent callers at
     * most one receives a claim token; it must be presented to every update
     * until the attempt ends.
     *
     * @param claimDuration how long the claim is exclusive before another
     *        worker may take a stalled run over
     */
    Optional<UUID> claim(UUID runId, Duration claimDuration);

    AutomaticBillingRun startStep(UUID runId, UUID claimToken, AutomaticBillingStep step);

    /**
     * Records {@code step} as completed and the references it produced
     * ({@code null} for none). A reference already recorded is never replaced
     * by a different one.
     */
    AutomaticBillingRun completeStep(UUID runId, UUID claimToken, AutomaticBillingStep step,
            UUID billingSnapshotId, UUID invoiceId);

    /**
     * Ends the attempt as FAILED at its current step. {@code reason} is
     * sanitized (credentials, tokens and authorization values removed) and
     * truncated before it is stored.
     */
    AutomaticBillingRun markFailed(UUID runId, UUID claimToken, String reason, LocalDateTime nextRetryAt);

    /**
     * Extends the current worker's claim to {@code now + claimDuration} - called
     * before every step, so only a run whose worker stopped renewing (crash,
     * restart, stall) can be taken over by another worker.
     *
     * @throws com.AccountReceivableManagement.global_exception_handler.AutomaticBillingRunClaimLostException
     *         if the caller no longer owns the run
     */
    AutomaticBillingRun renewClaim(UUID runId, UUID claimToken, Duration claimDuration);

    /**
     * Ends an interrupted run that has no automatic attempt left: a
     * PROCESSING run whose claim has expired becomes FAILED (at the step it
     * was interrupted in, no further automatic retry). Does nothing - and
     * returns empty - if the run is no longer PROCESSING or its claim is still
     * valid, i.e. a worker is still on it.
     */
    Optional<AutomaticBillingRun> failInterruptedRun(UUID runId, String reason);

    /** Ends the attempt: the invoice has been submitted for the existing manual approval. */
    AutomaticBillingRun markPendingApproval(UUID runId, UUID claimToken);

    /** Ends the attempt with nothing further for automation to do. */
    AutomaticBillingRun markCompleted(UUID runId, UUID claimToken);

    /** PENDING_APPROVAL -> COMPLETED once the submitted invoice has been approved. */
    AutomaticBillingRun completeAfterApproval(UUID runId);

    Optional<AutomaticBillingRun> findById(UUID runId);
}
