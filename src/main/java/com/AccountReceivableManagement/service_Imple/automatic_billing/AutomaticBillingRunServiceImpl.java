package com.AccountReceivableManagement.service_Imple.automatic_billing;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingPeriodDto;
import com.AccountReceivableManagement.entity.automatic_billing.AutomaticBillingRun;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingStep;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType;
import com.AccountReceivableManagement.global_exception_handler.AutomaticBillingRunClaimLostException;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.automatic_billing.AutomaticBillingRunRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.service_Imple.projectbilling_config.BillingOccurrenceServiceImpl;
import com.AccountReceivableManagement.service_interface.automatic_billing.AutomaticBillingRunService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Owns {@link AutomaticBillingRun} state. Every write runs in its own
 * REQUIRES_NEW transaction and commits before returning - a recorded failure
 * survives the rollback of whatever the caller was doing, and no transaction
 * is ever held open across an external call. Updates of a PROCESSING run
 * re-read it under a row lock and require the claim token of the worker
 * that owns the current attempt.
 */
@Slf4j
@Service
public class AutomaticBillingRunServiceImpl implements AutomaticBillingRunService {

    private static final String REDACTED = "[REDACTED]";

    private static final Pattern BEARER_VALUE = Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|secret|client[_-]?secret|api[_-]?key|access[_-]?token|refresh[_-]?token"
                    + "|token|authorization|cookie|credentials?)(\\s*[=:]\\s*)(\"[^\"]*\"|'[^']*'|[^\\s,;&]+)");
    private static final Pattern URL_USER_INFO = Pattern.compile("(?i)([a-z][a-z0-9+.-]*://)[^/\\s:@]+:[^/\\s@]+@");
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");

    private final AutomaticBillingRunRepository automaticBillingRunRepository;
    private final BillingScheduleRepository billingScheduleRepository;
    private final BillingConfigurationRepository billingConfigurationRepository;
    private final BillingOccurrenceServiceImpl billingOccurrenceService;
    private final TransactionTemplate requiresNew;
    private final Clock clock;

    @Autowired
    public AutomaticBillingRunServiceImpl(AutomaticBillingRunRepository automaticBillingRunRepository,
            BillingScheduleRepository billingScheduleRepository,
            BillingConfigurationRepository billingConfigurationRepository,
            BillingOccurrenceServiceImpl billingOccurrenceService,
            PlatformTransactionManager transactionManager) {
        this(automaticBillingRunRepository, billingScheduleRepository, billingConfigurationRepository,
                billingOccurrenceService, transactionManager, Clock.systemDefaultZone());
    }

    AutomaticBillingRunServiceImpl(AutomaticBillingRunRepository automaticBillingRunRepository,
            BillingScheduleRepository billingScheduleRepository,
            BillingConfigurationRepository billingConfigurationRepository,
            BillingOccurrenceServiceImpl billingOccurrenceService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.automaticBillingRunRepository = automaticBillingRunRepository;
        this.billingScheduleRepository = billingScheduleRepository;
        this.billingConfigurationRepository = billingConfigurationRepository;
        this.billingOccurrenceService = billingOccurrenceService;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    // ================================================================ creation

    @Override
    public AutomaticBillingRun findOrCreateForOccurrence(UUID billingScheduleId) {
        return findOrCreate(
                () -> automaticBillingRunRepository.findByBillingScheduleId(billingScheduleId)
                        .or(() -> relinkRunOfReplacedOccurrence(billingScheduleId)),
                () -> {
                    BillingSchedule occurrence = billingScheduleRepository.findById(billingScheduleId)
                            .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                                    "Billing occurrence could not be found."));

                    BillingConfiguration configuration = occurrence.getBillingConfiguration();
                    requireAutomaticBilling(configuration);

                    if (!occurrence.isDueForBilling(today())) {
                        throw new GlobalExceptionHandler.ValidationException(
                                "Billing occurrence " + billingScheduleId + " is not due and unbilled, so no "
                                        + "automatic billing run is created for it.");
                    }

                    return AutomaticBillingRun.builder()
                            .billingConfiguration(configuration)
                            .billingScheduleId(occurrence.getBillingScheduleId())
                            .billingPeriodStart(occurrence.getPeriodStartDate())
                            .billingPeriodEnd(occurrence.getPeriodEndDate())
                            .build();
                });
    }

    @Override
    public AutomaticBillingRun findOrCreateForTimeAndMaterialPeriod(
            UUID billingConfigurationId, LocalDate billingPeriodStart, LocalDate billingPeriodEnd) {
        return findOrCreate(
                () -> automaticBillingRunRepository
                        .findByBillingConfiguration_BillingConfigurationIdAndBillingPeriodStartAndBillingPeriodEnd(
                                billingConfigurationId, billingPeriodStart, billingPeriodEnd),
                () -> {
                    BillingConfiguration configuration = billingConfigurationRepository.findById(billingConfigurationId)
                            .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                                    "Billing Configuration not found"));
                    requireAutomaticBilling(configuration);

                    LocalDate today = today();
                    // The configuration's own billing periods are the only valid T&M periods.
                    BillingPeriodDto period = billingOccurrenceService
                            .resolveTimeAndMaterialBillingPeriods(billingConfigurationId, today).stream()
                            .filter(candidate -> candidate.getPeriodStartDate().equals(billingPeriodStart)
                                    && candidate.getPeriodEndDate().equals(billingPeriodEnd))
                            .findFirst()
                            .orElseThrow(() -> new GlobalExceptionHandler.ValidationException(
                                    "Billing period " + billingPeriodStart + " to " + billingPeriodEnd
                                            + " is not one of this configuration's billing periods."));

                    if (period.getBillingDate().isAfter(today)) {
                        throw new GlobalExceptionHandler.ValidationException(
                                "Billing period " + billingPeriodStart + " to " + billingPeriodEnd
                                        + " is not due until " + period.getBillingDate() + ".");
                    }

                    return AutomaticBillingRun.builder()
                            .billingConfiguration(configuration)
                            .billingPeriodStart(billingPeriodStart)
                            .billingPeriodEnd(billingPeriodEnd)
                            .build();
                });
    }

    /**
     * The run of the occurrence's billing event (configuration + period) when
     * it still refers to an occurrence that no longer exists - the occurrence
     * was removed and re-created for the same period. The run is the billing
     * event's one run (uk_automatic_billing_run_period), so it is re-linked to
     * the replacement instead of inserting a second run that the key would
     * reject on every scheduler pass. A run linked to another existing
     * occurrence, or already past SCHEDULED/FAILED, is a genuine conflict and
     * is reported.
     */
    private Optional<AutomaticBillingRun> relinkRunOfReplacedOccurrence(UUID billingScheduleId) {
        BillingSchedule occurrence = billingScheduleRepository.findById(billingScheduleId).orElse(null);
        if (occurrence == null || occurrence.getBillingConfiguration() == null) {
            return Optional.empty();
        }
        return automaticBillingRunRepository
                .findByBillingConfiguration_BillingConfigurationIdAndBillingPeriodStartAndBillingPeriodEnd(
                        occurrence.getBillingConfiguration().getBillingConfigurationId(),
                        occurrence.getPeriodStartDate(), occurrence.getPeriodEndDate())
                .map(run -> {
                    UUID previous = run.getBillingScheduleId();
                    if (billingScheduleId.equals(previous)) {
                        return run; // created concurrently for this very occurrence
                    }
                    if (previous == null || billingScheduleRepository.existsById(previous)
                            || automaticBillingRunRepository.relinkOccurrence(run.getAutomaticBillingRunId(), previous,
                            billingScheduleId, now(), AutomaticBillingRunStatus.CLAIMABLE) != 1) {
                        throw new GlobalExceptionHandler.ValidationException(
                                "Billing period " + occurrence.getPeriodStartDate() + " to "
                                        + occurrence.getPeriodEndDate() + " already has automatic billing run "
                                        + run.getAutomaticBillingRunId() + " (" + run.getStatus()
                                        + ") for occurrence " + previous + "; it is not re-linked to occurrence "
                                        + billingScheduleId + ".");
                    }
                    log.info("[AutomaticBillingRunRelinked] run={} occurrence {} -> {} (period {} to {})",
                            run.getAutomaticBillingRunId(), previous, billingScheduleId,
                            occurrence.getPeriodStartDate(), occurrence.getPeriodEndDate());
                    return automaticBillingRunRepository.findById(run.getAutomaticBillingRunId()).orElseThrow();
                });
    }

    /**
     * Returns the existing run, or inserts the new one. A concurrent creator
     * that commits first makes this insert fail on a unique key; the winner's
     * run is then read and returned in a fresh transaction, so every caller
     * ends up with the one run of the billing event.
     */
    private AutomaticBillingRun findOrCreate(Supplier<Optional<AutomaticBillingRun>> findExisting,
            Supplier<AutomaticBillingRun> newRun) {
        try {
            return requiresNew.execute(status -> findExisting.get()
                    .orElseGet(() -> automaticBillingRunRepository.saveAndFlush(newRun.get())));
        } catch (DataIntegrityViolationException ex) {
            log.info("[AutomaticBillingRunConcurrentCreate] Run was created concurrently; using the existing run. "
                    + "Reason: {}", ex.getMostSpecificCause().getMessage());
            return requiresNew.execute(status -> findExisting.get()
                    .orElseThrow(() -> ex));
        }
    }

    private void requireAutomaticBilling(BillingConfiguration configuration) {
        if (configuration.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Configuration must be APPROVED for automatic billing. Current status: "
                            + configuration.getApprovalStatus());
        }
        if (configuration.getInvoiceGenerationType() != InvoiceGenerationType.AUTOMATIC) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Configuration " + configuration.getBillingConfigurationId()
                            + " does not use AUTOMATIC invoice generation.");
        }
    }

    // ================================================================ claiming

    @Override
    public Optional<UUID> claim(UUID runId, Duration claimDuration) {
        if (claimDuration == null || claimDuration.isNegative() || claimDuration.isZero()) {
            throw new IllegalArgumentException("Claim duration must be positive.");
        }
        LocalDateTime now = now();
        UUID claimToken = UUID.randomUUID();

        Integer updated = requiresNew.execute(status -> automaticBillingRunRepository.claim(
                runId, claimToken, now.plus(claimDuration), now, AutomaticBillingRunStatus.CLAIMABLE));

        if (updated != null && updated == 1) {
            log.info("[AutomaticBillingRunClaimed] run={} claimedUntil={}", runId, now.plus(claimDuration));
            return Optional.of(claimToken);
        }
        return Optional.empty();
    }

    // ================================================================ owned updates

    @Override
    public AutomaticBillingRun startStep(UUID runId, UUID claimToken, AutomaticBillingStep step) {
        return updateOwnedRun(runId, claimToken, run -> {
            if (!step.isAfter(run.getLastCompletedStep())) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Step " + step + " has already been completed for automatic billing run " + runId + ".");
            }
            run.setCurrentStep(step);
        });
    }

    @Override
    public AutomaticBillingRun completeStep(UUID runId, UUID claimToken, AutomaticBillingStep step,
            UUID billingSnapshotId, UUID invoiceId) {
        return updateOwnedRun(runId, claimToken, run -> {
            if (run.getCurrentStep() != step) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Step " + step + " is not the step in progress (" + run.getCurrentStep()
                                + ") for automatic billing run " + runId + ".");
            }
            run.setBillingSnapshotId(keepReference(run.getBillingSnapshotId(), billingSnapshotId,
                    "billing snapshot", runId));
            run.setInvoiceId(keepReference(run.getInvoiceId(), invoiceId, "invoice", runId));
            run.setLastCompletedStep(step);
            run.setCurrentStep(null);
        });
    }

    private UUID keepReference(UUID recorded, UUID produced, String what, UUID runId) {
        if (produced == null) {
            return recorded;
        }
        if (recorded != null && !recorded.equals(produced)) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Automatic billing run " + runId + " already references " + what + " " + recorded
                            + "; it cannot be replaced by " + produced + ".");
        }
        return produced;
    }

    @Override
    public AutomaticBillingRun markFailed(UUID runId, UUID claimToken, String reason, LocalDateTime nextRetryAt) {
        return updateOwnedRun(runId, claimToken, run -> {
            transition(run, AutomaticBillingRunStatus.FAILED);
            // currentStep is kept: it is the step the attempt failed at.
            run.setFailureStep(run.getCurrentStep());
            run.setFailureReason(sanitizeFailureReason(reason));
            run.setFailedAt(now());
            run.setNextRetryAt(nextRetryAt);
            releaseClaim(run);
            log.warn("[AutomaticBillingRunFailed] run={} step={} attempt={} nextRetryAt={}", runId,
                    run.getFailureStep(), run.getAttemptCount(), nextRetryAt);
        });
    }

    @Override
    public AutomaticBillingRun renewClaim(UUID runId, UUID claimToken, Duration claimDuration) {
        if (claimDuration == null || claimDuration.isNegative() || claimDuration.isZero()) {
            throw new IllegalArgumentException("Claim duration must be positive.");
        }
        return updateOwnedRun(runId, claimToken, run -> run.setClaimExpiresAt(now().plus(claimDuration)));
    }

    @Override
    public Optional<AutomaticBillingRun> failInterruptedRun(UUID runId, String reason) {
        return Optional.ofNullable(requiresNew.execute(status -> {
            AutomaticBillingRun run = lockRun(runId);
            LocalDateTime now = now();
            if (run.getStatus() != AutomaticBillingRunStatus.PROCESSING
                    || run.getClaimExpiresAt() == null || !run.getClaimExpiresAt().isBefore(now)) {
                return null;
            }
            transition(run, AutomaticBillingRunStatus.FAILED);
            run.setFailureStep(run.getCurrentStep());
            run.setFailureReason(sanitizeFailureReason(reason));
            run.setFailedAt(now);
            run.setNextRetryAt(null);
            releaseClaim(run);
            log.warn("[AutomaticBillingRunInterrupted] run={} step={} attempt={} - no automatic attempt left",
                    runId, run.getFailureStep(), run.getAttemptCount());
            return automaticBillingRunRepository.saveAndFlush(run);
        }));
    }

    @Override
    public AutomaticBillingRun markPendingApproval(UUID runId, UUID claimToken) {
        return updateOwnedRun(runId, claimToken, run -> {
            if (run.getLastCompletedStep() != AutomaticBillingStep.SUBMIT_FOR_APPROVAL || run.getInvoiceId() == null) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Automatic billing run " + runId + " can only await approval once its invoice "
                                + "has been submitted for approval.");
            }
            transition(run, AutomaticBillingRunStatus.PENDING_APPROVAL);
            run.setCurrentStep(null);
            run.setNextRetryAt(null);
            releaseClaim(run);
        });
    }

    @Override
    public AutomaticBillingRun markCompleted(UUID runId, UUID claimToken) {
        return updateOwnedRun(runId, claimToken, run -> {
            requireInvoice(run);
            transition(run, AutomaticBillingRunStatus.COMPLETED);
            finish(run);
        });
    }

    @Override
    public AutomaticBillingRun completeAfterApproval(UUID runId) {
        return requiresNew.execute(status -> {
            AutomaticBillingRun run = lockRun(runId);
            transition(run, AutomaticBillingRunStatus.COMPLETED);
            finish(run);
            return automaticBillingRunRepository.saveAndFlush(run);
        });
    }

    @Override
    public Optional<AutomaticBillingRun> findById(UUID runId) {
        return automaticBillingRunRepository.findById(runId);
    }

    /**
     * Applies {@code mutation} to the run only for the worker that owns its
     * current attempt: the run is re-read under a row lock, must still be
     * PROCESSING, and must carry the caller's claim token. Otherwise the
     * caller has lost the run (expired claim taken over, or attempt already
     * ended) and nothing is changed.
     */
    private AutomaticBillingRun updateOwnedRun(UUID runId, UUID claimToken, Consumer<AutomaticBillingRun> mutation) {
        return requiresNew.execute(status -> {
            AutomaticBillingRun run = lockRun(runId);
            if (run.getStatus() != AutomaticBillingRunStatus.PROCESSING
                    || claimToken == null
                    || !claimToken.equals(run.getClaimToken())) {
                throw new AutomaticBillingRunClaimLostException(
                        "Automatic billing run " + runId + " is not owned by this worker (status "
                                + run.getStatus() + ").");
            }
            mutation.accept(run);
            return automaticBillingRunRepository.saveAndFlush(run);
        });
    }

    private AutomaticBillingRun lockRun(UUID runId) {
        return automaticBillingRunRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                        "Automatic billing run could not be found."));
    }

    private void transition(AutomaticBillingRun run, AutomaticBillingRunStatus target) {
        if (!run.getStatus().canTransitionTo(target)) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Automatic billing run " + run.getAutomaticBillingRunId() + " cannot move from "
                            + run.getStatus() + " to " + target + ".");
        }
        run.setStatus(target);
    }

    private void requireInvoice(AutomaticBillingRun run) {
        if (run.getInvoiceId() == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Automatic billing run " + run.getAutomaticBillingRunId()
                            + " cannot complete without an invoice.");
        }
    }

    private void finish(AutomaticBillingRun run) {
        run.setCurrentStep(null);
        run.setNextRetryAt(null);
        run.setCompletedAt(now());
        releaseClaim(run);
    }

    private void releaseClaim(AutomaticBillingRun run) {
        run.setClaimToken(null);
        run.setClaimExpiresAt(null);
    }

    // ================================================================ failure text

    /**
     * Makes a failure message safe to persist and display: removes bearer
     * tokens, JWTs, credentials embedded in URLs and any
     * password/secret/token/authorization assignment, strips control
     * characters and truncates to the column length. Applied to every
     * stored failure reason - exception messages (e.g. a JDBC or HTTP client
     * error) can echo connection strings or headers.
     */
    static String sanitizeFailureReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Unknown failure.";
        }
        String sanitized = reason;
        sanitized = BEARER_VALUE.matcher(sanitized).replaceAll("Bearer " + REDACTED);
        sanitized = JWT.matcher(sanitized).replaceAll(REDACTED);
        sanitized = URL_USER_INFO.matcher(sanitized).replaceAll("$1" + REDACTED + "@");
        sanitized = SECRET_ASSIGNMENT.matcher(sanitized).replaceAll("$1$2" + REDACTED);
        sanitized = CONTROL_CHARACTERS.matcher(sanitized).replaceAll(" ").trim();

        if (sanitized.length() > AutomaticBillingRun.FAILURE_REASON_MAX_LENGTH) {
            sanitized = sanitized.substring(0, AutomaticBillingRun.FAILURE_REASON_MAX_LENGTH);
        }
        return sanitized;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
