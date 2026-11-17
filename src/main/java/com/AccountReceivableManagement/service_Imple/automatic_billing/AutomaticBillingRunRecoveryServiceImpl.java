package com.AccountReceivableManagement.service_Imple.automatic_billing;

import com.AccountReceivableManagement.dto.recovery.AutomaticBillingRunEventDto;
import com.AccountReceivableManagement.dto.recovery.RecoveryResultDto;
import com.AccountReceivableManagement.entity.automatic_billing.AutomaticBillingRun;
import com.AccountReceivableManagement.entity.automatic_billing.AutomaticBillingRunEvent;
import com.AccountReceivableManagement.entity.invoice_generation.Invoice;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunEventType;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.automatic_billing.AutomaticBillingRunEventRepository;
import com.AccountReceivableManagement.repo.automatic_billing.AutomaticBillingRunRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository;
import com.AccountReceivableManagement.security.Actor;
import com.AccountReceivableManagement.security.FinancialAction;
import com.AccountReceivableManagement.security.FinancialActionAuthorizer;
import com.AccountReceivableManagement.service_interface.automatic_billing.AutomaticBillingRunRecoveryService;
import com.AccountReceivableManagement.service_interface.automatic_billing.AutomaticBillingRunService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * See {@link AutomaticBillingRunRecoveryService}. Every action re-reads the
 * run under a row lock, so concurrent operators (or an operator and the
 * scheduler) serialize: the second sees the first's result and is rejected
 * as already done instead of acting twice.
 */
@Slf4j
@Service
public class AutomaticBillingRunRecoveryServiceImpl implements AutomaticBillingRunRecoveryService {

    static final String RECORD_TYPE = "AUTOMATIC_BILLING_RUN";

    /** Persisted invoice statuses that mean Finance has approved the invoice. */
    static final Set<InvoiceStatus> APPROVED_INVOICE_STATUSES =
            EnumSet.of(InvoiceStatus.APPROVED, InvoiceStatus.READY_TO_SEND);

    private final AutomaticBillingRunRepository runRepository;
    private final AutomaticBillingRunEventRepository eventRepository;
    private final AutomaticBillingRunService runService;
    private final InvoiceRepository invoiceRepository;
    private final FinancialActionAuthorizer authorizer;
    private final TransactionTemplate transaction;
    private final Clock clock;

    @Autowired
    public AutomaticBillingRunRecoveryServiceImpl(AutomaticBillingRunRepository runRepository,
            AutomaticBillingRunEventRepository eventRepository,
            AutomaticBillingRunService runService,
            InvoiceRepository invoiceRepository,
            FinancialActionAuthorizer authorizer,
            PlatformTransactionManager transactionManager) {
        this(runRepository, eventRepository, runService, invoiceRepository, authorizer, transactionManager,
                Clock.systemDefaultZone());
    }

    AutomaticBillingRunRecoveryServiceImpl(AutomaticBillingRunRepository runRepository,
            AutomaticBillingRunEventRepository eventRepository,
            AutomaticBillingRunService runService,
            InvoiceRepository invoiceRepository,
            FinancialActionAuthorizer authorizer,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.runRepository = runRepository;
        this.eventRepository = eventRepository;
        this.runService = runService;
        this.invoiceRepository = invoiceRepository;
        this.authorizer = authorizer;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    // ================================================================ G-17 requeue

    @Override
    public RecoveryResultDto requeue(UUID runId, String reason) {
        Actor actor = authorizer.requirePermission(FinancialAction.OPERATIONS_RECOVERY);
        String why = requireReason(reason);

        AutomaticBillingRun current = runRepository.findById(runId).orElseThrow(() -> notFound(runId));
        if (isInterrupted(current)) {
            // Its worker is gone; close the attempt (failure recorded) so it can be re-queued.
            runService.failInterruptedRun(runId, "Interrupted during " + current.getCurrentStep()
                    + " on attempt " + current.getAttemptCount() + "; closed for re-queue by an operator.");
        }

        return transaction.execute(status -> {
            AutomaticBillingRun run = runRepository.findByIdForUpdate(runId).orElseThrow(() -> notFound(runId));
            LocalDateTime now = now();
            switch (run.getStatus()) {
                case FAILED -> {
                    if (alreadyRequeued(run)) {
                        return rejected(run, "Run was already re-queued at " + run.getRequeuedAt()
                                + " and is due at " + run.getNextRetryAt() + "; it has not failed since.");
                    }
                    record(run, AutomaticBillingRunEventType.REQUEUED, AutomaticBillingRunStatus.FAILED, actor, why);
                    run.setNextRetryAt(now);
                    run.setRequeuedAt(now);
                    runRepository.saveAndFlush(run);
                    log.warn("[AutomaticBillingRunRequeued] run={} failureStep={} attempt={} by={}",
                            runId, run.getFailureStep(), run.getAttemptCount(), actor.label());
                    return accepted(run, "Run re-queued; the next scheduler tick processes it, resuming "
                            + "after its last completed step. Its failure details are kept in its history.");
                }
                case PROCESSING -> {
                    return rejected(run, "Run is being processed by a worker (claim valid until "
                            + run.getClaimExpiresAt() + "); it cannot be re-queued.");
                }
                case SCHEDULED -> {
                    return rejected(run, "Run is already queued for processing.");
                }
                case PENDING_APPROVAL -> {
                    return rejected(run, "Run's invoice is awaiting Finance approval; nothing to re-queue. "
                            + "If the invoice is already approved, reconcile the run instead.");
                }
                case COMPLETED -> {
                    return rejected(run, "Run is completed; nothing to re-queue.");
                }
                default -> throw new IllegalStateException("Unknown run status " + run.getStatus());
            }
        });
    }

    // ================================================================ G-18 approval reconciliation

    @Override
    public RecoveryResultDto reconcileApproval(UUID runId) {
        Actor actor = authorizer.requirePermission(FinancialAction.OPERATIONS_RECOVERY);
        return reconcile(runId, actor);
    }

    @Override
    public int reconcileApprovedRuns(int limit) {
        if (limit <= 0) {
            return 0;
        }
        int completed = 0;
        for (UUID runId : runRepository.findPendingApprovalRunIdsWithInvoiceIn(
                APPROVED_INVOICE_STATUSES, PageRequest.of(0, limit))) {
            try {
                if (reconcile(runId, Actor.SYSTEM).isAccepted()) {
                    completed++;
                }
            } catch (RuntimeException ex) {
                log.warn("[AutomaticBillingRunReconciliation] run {} could not be reconciled: {}", runId,
                        AutomaticBillingRunServiceImpl.sanitizeFailureReason(ex.getMessage()));
            }
        }
        return completed;
    }

    /**
     * Completes a PENDING_APPROVAL run from its invoice's persisted status.
     * Never approves anything: only an invoice Finance already approved
     * completes its run. A rejected invoice keeps its run waiting - the
     * invoice goes back through correction and approval.
     */
    private RecoveryResultDto reconcile(UUID runId, Actor actor) {
        return transaction.execute(status -> {
            AutomaticBillingRun run = runRepository.findByIdForUpdate(runId).orElseThrow(() -> notFound(runId));
            if (run.getStatus() != AutomaticBillingRunStatus.PENDING_APPROVAL) {
                return rejected(run, "Run is " + run.getStatus() + "; only a run awaiting approval is reconciled.");
            }
            Invoice invoice = run.getInvoiceId() == null ? null
                    : invoiceRepository.findById(run.getInvoiceId()).orElse(null);
            if (invoice == null) {
                return rejected(run, "Run's invoice could not be found; investigate before acting.");
            }
            if (!APPROVED_INVOICE_STATUSES.contains(invoice.getStatus())) {
                return rejected(run, invoice.getStatus() == InvoiceStatus.REJECTED
                        ? "Invoice was rejected; the run waits for the corrected invoice to be approved."
                        : "Invoice is " + invoice.getStatus() + "; the run waits for Finance approval.");
            }
            record(run, AutomaticBillingRunEventType.APPROVAL_RECONCILED, AutomaticBillingRunStatus.COMPLETED,
                    actor, "Invoice is " + invoice.getStatus() + "; run completion after approval had not been recorded.");
            run.setStatus(AutomaticBillingRunStatus.COMPLETED);
            run.setCurrentStep(null);
            run.setNextRetryAt(null);
            run.setCompletedAt(now());
            run.setClaimToken(null);
            run.setClaimExpiresAt(null);
            runRepository.saveAndFlush(run);
            log.info("[AutomaticBillingRunReconciled] run={} invoiceStatus={} by={}", runId, invoice.getStatus(),
                    actor.label());
            return accepted(run, "Run completed: its invoice is " + invoice.getStatus() + ".");
        });
    }

    // ================================================================ history

    @Override
    public List<AutomaticBillingRunEventDto> getEvents(UUID runId) {
        authorizer.requirePermission(FinancialAction.OPERATIONS_RECOVERY);
        if (!runRepository.existsById(runId)) {
            throw notFound(runId);
        }
        return eventRepository.findByRunIdOrderByOccurredAtAsc(runId).stream()
                .map(event -> AutomaticBillingRunEventDto.builder()
                        .eventId(event.getAutomaticBillingRunEventId())
                        .runId(event.getRunId())
                        .eventType(event.getEventType().name())
                        .statusBefore(event.getStatusBefore().name())
                        .statusAfter(event.getStatusAfter().name())
                        .failureStep(event.getFailureStep() != null ? event.getFailureStep().name() : null)
                        .failureReason(event.getFailureReason())
                        .failedAt(event.getFailedAt())
                        .attemptCount(event.getAttemptCount())
                        .actor(event.getActor())
                        .actorUserId(event.getActorUserId())
                        .reason(event.getReason())
                        .occurredAt(event.getOccurredAt())
                        .build())
                .toList();
    }

    // ================================================================ helpers

    private boolean isInterrupted(AutomaticBillingRun run) {
        return run.getStatus() == AutomaticBillingRunStatus.PROCESSING
                && run.getClaimExpiresAt() != null && run.getClaimExpiresAt().isBefore(now());
    }

    /** Re-queued since its last failure and not yet attempted again. */
    private static boolean alreadyRequeued(AutomaticBillingRun run) {
        return run.getRequeuedAt() != null && run.getNextRetryAt() != null
                && (run.getFailedAt() == null || !run.getRequeuedAt().isBefore(run.getFailedAt()));
    }

    private void record(AutomaticBillingRun run, AutomaticBillingRunEventType type,
            AutomaticBillingRunStatus statusAfter, Actor actor, String reason) {
        eventRepository.save(AutomaticBillingRunEvent.builder()
                .runId(run.getAutomaticBillingRunId())
                .eventType(type)
                .statusBefore(run.getStatus())
                .statusAfter(statusAfter)
                .failureStep(run.getFailureStep())
                .failureReason(run.getFailureReason())
                .failedAt(run.getFailedAt())
                .attemptCount(run.getAttemptCount())
                .actor(actor.label())
                .actorUserId(actor.userId())
                .reason(reason)
                .occurredAt(now())
                .build());
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new GlobalExceptionHandler.ValidationException("A reason is required for a recovery action.");
        }
        String trimmed = AutomaticBillingRunServiceImpl.sanitizeFailureReason(reason.trim());
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    private static RecoveryResultDto accepted(AutomaticBillingRun run, String message) {
        return result(run, true, message);
    }

    private static RecoveryResultDto rejected(AutomaticBillingRun run, String message) {
        return result(run, false, message);
    }

    private static RecoveryResultDto result(AutomaticBillingRun run, boolean accepted, String message) {
        return RecoveryResultDto.builder()
                .recordId(run.getAutomaticBillingRunId())
                .recordType(RECORD_TYPE)
                .accepted(accepted)
                .state(run.getStatus().name())
                .message(message)
                .build();
    }

    private static GlobalExceptionHandler.ResourceNotFoundException notFound(UUID runId) {
        return new GlobalExceptionHandler.ResourceNotFoundException(
                "Automatic billing run " + runId + " could not be found.");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
