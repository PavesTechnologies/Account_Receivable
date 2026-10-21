package com.AccountReceivableManagement.service_interface.automatic_billing;

import com.AccountReceivableManagement.dto.recovery.AutomaticBillingRunEventDto;
import com.AccountReceivableManagement.dto.recovery.RecoveryResultDto;

import java.util.List;
import java.util.UUID;

/**
 * Recovery of automatic billing runs outside their normal processing.
 * Operator actions require {@code FinancialAction.OPERATIONS_RECOVERY} and
 * are recorded with the actor and reason; they never approve an invoice and
 * never bypass the run's own idempotent processing.
 */
public interface AutomaticBillingRunRecoveryService {

    /**
     * G-17: makes one FAILED run (or an interrupted PROCESSING run, closed
     * as FAILED first) due for another attempt now. Its failure details stay
     * on the run and are copied into the run's event history. Rejected for
     * SCHEDULED, actively PROCESSING, PENDING_APPROVAL and COMPLETED runs,
     * and for a run already re-queued since its last failure.
     */
    RecoveryResultDto requeue(UUID runId, String reason);

    /**
     * G-18 (operator): completes a PENDING_APPROVAL run whose invoice is
     * already approved according to its persisted status.
     */
    RecoveryResultDto reconcileApproval(UUID runId);

    /**
     * G-18 (scheduler, as the system): completes up to {@code limit}
     * PENDING_APPROVAL runs whose invoices are already approved.
     *
     * @return how many runs were completed
     */
    int reconcileApprovedRuns(int limit);

    List<AutomaticBillingRunEventDto> getEvents(UUID runId);
}
