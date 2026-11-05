package com.AccountReceivableManagement.service_Imple.automatic_billing;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotCreateRequestDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotResponseDto;
import com.AccountReceivableManagement.dto.common.ApiResponse;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.dto.tax_calculation.TaxCalculationResponseDto;
import com.AccountReceivableManagement.entity.automatic_billing.AutomaticBillingRun;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingStep;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType;
import com.AccountReceivableManagement.global_exception_handler.AutomaticBillingRunClaimLostException;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.integration.billing_data_acquisition.BillingConfigurationIntegration;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.service_Imple.projectbilling_config.BillingOccurrenceServiceImpl;
import com.AccountReceivableManagement.service_interface.automatic_billing.AutomaticBillingRunService;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingSnapshotService;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingOccurrenceTransactionService;
import com.AccountReceivableManagement.service_interface.tax_calculation.TaxCalculationService;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Drives one {@link AutomaticBillingRun} through the existing billing
 * services, up to and including submission for the existing Finance Manager
 * approval - never further: approval, invoice generation and sending stay
 * manual.
 * <pre>
 * claim run ─▶ validate ─▶ SOURCE_DATA_ACQUISITION ─▶ TAX_CALCULATION
 *                          ─▶ INVOICE_CREATION ─▶ SUBMIT_FOR_APPROVAL ─▶ PENDING_APPROVAL
 * </pre>
 * One pipeline for every billing type; only source data acquisition differs:
 * <ul>
 *   <li>Time &amp; Material (run without an occurrence): the existing Billing
 *       Snapshot acquisition (TMS, rate card, validator) for the run's own
 *       billing period, then tax and invoice against the snapshot;</li>
 *   <li>Fixed Price, Milestone / Installment, Recurring (run for a Billing
 *       Occurrence): the occurrence already carries its amount - it is made
 *       TAX_PENDING if the occurrence scheduler has not done so yet - then tax
 *       and invoice against the occurrence (which also produces the
 *       type-specific invoice line).</li>
 * </ul>
 * Holds no business logic of its own and no transaction: every service it
 * calls commits on its own, and run state is recorded by
 * {@link AutomaticBillingRunService} (REQUIRES_NEW) between those calls - so
 * nothing is held open across the TMS call, and a failure leaves every
 * completed step committed for the next attempt to resume from.
 * <p>
 * Idempotent: steps already recorded as completed are skipped, and every
 * step uses the existing find-or-create operation (snapshot reuse per
 * project/period, findOrCalculateTax*, findOrGenerateInvoice*) backed by the
 * existing unique keys, so a repeated or resumed run never duplicates a
 * snapshot, tax calculation, invoice or invoice line.
 */
@Slf4j
@Service
public class AutomaticBillingOrchestrator {

    private final AutomaticBillingRunService automaticBillingRunService;
    private final BillingConfigurationIntegration billingConfigurationIntegration;
    private final BillingScheduleRepository billingScheduleRepository;
    private final BillingOccurrenceTransactionService billingOccurrenceTransactionService;
    private final BillingOccurrenceServiceImpl billingOccurrenceService;
    private final BillingSnapshotService billingSnapshotService;
    private final TaxCalculationService taxCalculationService;
    private final InvoiceService invoiceService;
    private final AutomaticBillingRetryPolicy retryPolicy;
    private final Duration claimDuration;

    public AutomaticBillingOrchestrator(AutomaticBillingRunService automaticBillingRunService,
            BillingConfigurationIntegration billingConfigurationIntegration,
            BillingScheduleRepository billingScheduleRepository,
            BillingOccurrenceTransactionService billingOccurrenceTransactionService,
            BillingOccurrenceServiceImpl billingOccurrenceService,
            BillingSnapshotService billingSnapshotService,
            TaxCalculationService taxCalculationService,
            InvoiceService invoiceService,
            AutomaticBillingRetryPolicy retryPolicy,
            @Value("${ar.automatic-billing.claim-duration:PT15M}") Duration claimDuration) {
        this.automaticBillingRunService = automaticBillingRunService;
        this.billingConfigurationIntegration = billingConfigurationIntegration;
        this.billingScheduleRepository = billingScheduleRepository;
        this.billingOccurrenceTransactionService = billingOccurrenceTransactionService;
        this.billingOccurrenceService = billingOccurrenceService;
        this.billingSnapshotService = billingSnapshotService;
        this.taxCalculationService = taxCalculationService;
        this.invoiceService = invoiceService;
        this.retryPolicy = retryPolicy;
        this.claimDuration = claimDuration;
    }

    /**
     * Processes the run from wherever it stands. Returns the run's state
     * afterwards. Does nothing - and returns the run unchanged - when the run
     * has already handed over to approval or completed, is owned by another
     * worker, or is FAILED and not yet due for another attempt. A failure of
     * any step is recorded on the run (FAILED, failed step, sanitized reason)
     * and not rethrown; the run stays recoverable.
     *
     * @throws GlobalExceptionHandler.ResourceNotFoundException if the run does not exist
     */
    public AutomaticBillingRun processRun(UUID automaticBillingRunId) {
        // Correlation for every line logged while this run is processed - here and in
        // the billing services it calls (logging.pattern.level prints [run=...]).
        try (MDC.MDCCloseable runId = MDC.putCloseable(MDC_RUN_ID, automaticBillingRunId.toString())) {
            // Automatic billing acts as the system: what it records is attributed to the
            // system, and it can never take a decision reserved for an authorized person.
            AutomaticBillingRun result = com.AccountReceivableManagement.security.ActorContext.callAsSystem(
                    () -> processRunInContext(automaticBillingRunId));
            logOutcome(result);
            return result;
        } finally {
            MDC.remove(MDC_CONFIGURATION_ID);
            MDC.remove(MDC_OCCURRENCE_ID);
            MDC.remove(MDC_BILLING_TYPE);
            MDC.remove(MDC_BILLING_PERIOD);
        }
    }

    private AutomaticBillingRun processRunInContext(UUID automaticBillingRunId) {
        AutomaticBillingRun run = loadRun(automaticBillingRunId);
        MDC.put(MDC_CONFIGURATION_ID, String.valueOf(run.getBillingConfiguration().getBillingConfigurationId()));
        MDC.put(MDC_OCCURRENCE_ID, run.getBillingScheduleId() != null ? run.getBillingScheduleId().toString() : "-");
        MDC.put(MDC_BILLING_PERIOD, run.getBillingPeriodStart() + ".." + run.getBillingPeriodEnd());

        if (run.getStatus() == AutomaticBillingRunStatus.PENDING_APPROVAL
                || run.getStatus() == AutomaticBillingRunStatus.COMPLETED) {
            log.debug("[AutomaticBilling] run={} is {}; nothing to process.", automaticBillingRunId, run.getStatus());
            return run;
        }

        Optional<UUID> claim = automaticBillingRunService.claim(automaticBillingRunId, claimDuration);
        if (claim.isEmpty()) {
            log.info("[AutomaticBilling] run={} not claimed (status {}): owned by another worker or not due for "
                    + "another attempt.", automaticBillingRunId, run.getStatus());
            return loadRun(automaticBillingRunId);
        }
        UUID claimToken = claim.get();

        try {
            executeSteps(loadRun(automaticBillingRunId), claimToken);
        } catch (AutomaticBillingRunClaimLostException ex) {
            // Another worker took the run over; it is no longer ours to update.
            log.warn("[AutomaticBilling] run={} lost its claim during processing: {}",
                    automaticBillingRunId, ex.getMessage());
        } catch (RuntimeException ex) {
            recordFailure(automaticBillingRunId, claimToken, ex);
        }

        return loadRun(automaticBillingRunId);
    }

    // ================================================================ pipeline

    private void executeSteps(AutomaticBillingRun run, UUID claimToken) {
        UUID runId = run.getAutomaticBillingRunId();
        BillingSource source = resolveSource(run);

        if (!isCompleted(run, AutomaticBillingStep.SOURCE_DATA_ACQUISITION)) {
            startStep(runId, claimToken, AutomaticBillingStep.SOURCE_DATA_ACQUISITION);
            requireNoUndecidedFinanceTerms(run, source);
            UUID snapshotId = source.timeAndMaterial()
                    ? acquireTimeAndMaterialSnapshot(run, source)
                    : prepareOccurrenceForTax(source);
            run = automaticBillingRunService.completeStep(runId, claimToken,
                    AutomaticBillingStep.SOURCE_DATA_ACQUISITION, snapshotId, null);
        }

        if (!isCompleted(run, AutomaticBillingStep.TAX_CALCULATION)) {
            startStep(runId, claimToken, AutomaticBillingStep.TAX_CALCULATION);
            log.info("[AutomaticBillingTax] run={} configuration={} occurrence={} snapshot={} tax calculation started.",
                    runId, MDC.get(MDC_CONFIGURATION_ID), MDC.get(MDC_OCCURRENCE_ID),
                    run.getBillingSnapshotId() != null ? run.getBillingSnapshotId() : "-");
            TaxCalculationResponseDto tax = source.timeAndMaterial()
                    ? taxCalculationService.findOrCalculateTax(requireSnapshot(run))
                    : taxCalculationService.findOrCalculateTaxForSchedule(run.getBillingScheduleId());
            if (tax != null) {
                log.info("[AutomaticBillingTax] run={} configuration={} occurrence={} tax calculation {} completed: "
                                + "taxable={} tax={} grandTotal={} currency={}",
                        runId, MDC.get(MDC_CONFIGURATION_ID), MDC.get(MDC_OCCURRENCE_ID),
                        tax.getTaxCalculationId(), tax.getTaxableAmount(), tax.getTotalTaxAmount(),
                        tax.getGrandTotal(), tax.getCurrencyCode());
            }
            run = automaticBillingRunService.completeStep(runId, claimToken,
                    AutomaticBillingStep.TAX_CALCULATION, null, null);
        }

        if (!isCompleted(run, AutomaticBillingStep.INVOICE_CREATION)) {
            startStep(runId, claimToken, AutomaticBillingStep.INVOICE_CREATION);
            InvoiceResponseDto invoice = source.timeAndMaterial()
                    ? invoiceService.findOrGenerateInvoice(requireSnapshot(run))
                    : invoiceService.findOrGenerateInvoiceForSchedule(run.getBillingScheduleId());
            run = automaticBillingRunService.completeStep(runId, claimToken,
                    AutomaticBillingStep.INVOICE_CREATION, null, invoice.getInvoiceId());
        }

        if (!isCompleted(run, AutomaticBillingStep.SUBMIT_FOR_APPROVAL)) {
            startStep(runId, claimToken, AutomaticBillingStep.SUBMIT_FOR_APPROVAL);
            InvoiceStatus status = submitForApproval(run.getInvoiceId());
            run = automaticBillingRunService.completeStep(runId, claimToken,
                    AutomaticBillingStep.SUBMIT_FOR_APPROVAL, null, null);

            if (status == InvoiceStatus.APPROVED || status == InvoiceStatus.READY_TO_SEND) {
                // Already approved manually before automation reached it - nothing left to do.
                automaticBillingRunService.markCompleted(runId, claimToken);
                return;
            }
        }

        automaticBillingRunService.markPendingApproval(runId, claimToken);
        log.info("[AutomaticBilling] run={} submitted invoice {} for approval.", runId, run.getInvoiceId());
    }

    private boolean isCompleted(AutomaticBillingRun run, AutomaticBillingStep step) {
        return run.getLastCompletedStep() != null && !step.isAfter(run.getLastCompletedStep());
    }

    // ================================================================ validation & routing

    private record BillingSource(boolean timeAndMaterial, BillingType billingType, Long projectId,
            BillingSchedule occurrence) {
    }

    /**
     * Re-checked on every attempt: the configuration may have been edited
     * (back to PENDING_APPROVAL) or switched to MANUAL since the run was
     * created. The billing type comes from the existing configuration
     * integration and must agree with the run's own shape - a T&amp;M run has
     * no occurrence, every other run has one.
     */
    private BillingSource resolveSource(AutomaticBillingRun run) {
        UUID configurationId = run.getBillingConfiguration().getBillingConfigurationId();
        BillingConfigurationResponseDto configuration =
                billingConfigurationIntegration.getApprovedBillingConfigurationById(configurationId);

        if (configuration == null || !configuration.isApproved()) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Configuration " + configurationId + " is not APPROVED.");
        }
        if (configuration.getInvoiceGenerationType() != InvoiceGenerationType.AUTOMATIC) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Configuration " + configurationId + " no longer uses AUTOMATIC invoice generation.");
        }

        if (configuration.getBillingType() == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Configuration " + configurationId + " has unsupported or unknown billing type: "
                            + configuration.getBillingTypeName());
        }

        MDC.put(MDC_BILLING_TYPE, String.valueOf(configuration.getBillingType()));
        boolean timeAndMaterial = configuration.getBillingType() == BillingType.TIME_AND_MATERIAL;

        if (timeAndMaterial) {
            if (run.isOccurrenceBased()) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Billing Configuration " + configurationId + " is Time & Material but the run is for a "
                                + "Billing Occurrence; it cannot be processed.");
            }
            if (configuration.getProjectId() == null) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Billing Configuration " + configurationId + " has no project to acquire timesheets for.");
            }
            return new BillingSource(true, configuration.getBillingType(), configuration.getProjectId(), null);
        }

        if (!run.isOccurrenceBased()) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Configuration " + configurationId + " is " + configuration.getBillingTypeName()
                            + " but the run has no Billing Occurrence; it cannot be processed.");
        }

        BillingSchedule occurrence = billingScheduleRepository.findById(run.getBillingScheduleId())
                .orElseThrow(() -> new GlobalExceptionHandler.ValidationException(
                        "Billing Occurrence " + run.getBillingScheduleId() + " no longer exists."));
        if (!Boolean.TRUE.equals(occurrence.getIsActive())
                || occurrence.getPeriodStatus() == BillingPeriodStatus.CANCELLED) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Occurrence " + run.getBillingScheduleId() + " is no longer active.");
        }
        return new BillingSource(false, configuration.getBillingType(), configuration.getProjectId(), occurrence);
    }

    /**
     * G-32: a Fixed Price configuration with retention or an advance is not
     * billed automatically while Finance has not decided how those terms
     * change the invoice - nothing is created; the run fails permanently and
     * waits for a person (bill manually, or re-queue once decided).
     */
    private void requireNoUndecidedFinanceTerms(AutomaticBillingRun run, BillingSource source) {
        if (source.timeAndMaterial() || source.billingType() != BillingType.FIXED_PRICE) {
            return;
        }
        java.util.List<String> undecided = billingOccurrenceService.undecidedFixedPriceTerms(
                run.getBillingConfiguration().getBillingConfigurationId());
        if (undecided != null && !undecided.isEmpty()) {
            throw new GlobalExceptionHandler.ValidationException("Fixed Price configuration has "
                    + String.join(" and ", undecided) + ", which billing does not apply yet (Finance decision "
                    + "pending); it is not billed automatically - bill it manually after review.");
        }
    }

    // ================================================================ source data

    /**
     * T&amp;M: the existing snapshot acquisition, for exactly the run's billing
     * period (resolved from the configuration's frequency when the run was
     * created - never a calendar month). It reuses the project/period's
     * existing snapshot, so a resumed run does not call TMS again, and it
     * persists nothing unless acquisition and validation succeed.
     */
    private UUID acquireTimeAndMaterialSnapshot(AutomaticBillingRun run, BillingSource source) {
        ApiResponse<BillingSnapshotResponseDto> response = billingSnapshotService.createBillingSnapshot(
                BillingSnapshotCreateRequestDto.builder()
                        .projectId(source.projectId())
                        .billingConfigurationId(run.getBillingConfiguration().getBillingConfigurationId())
                        .billingPeriodStart(run.getBillingPeriodStart())
                        .billingPeriodEnd(run.getBillingPeriodEnd())
                        .build());

        if (response == null || !response.isSuccess() || response.getData() == null
                || response.getData().getSnapshotId() == null) {
            String message = response != null && response.getMessage() != null ? response.getMessage() : "";
            if (message.contains("not approved") || message.contains("pending approval")) {
                throw new com.AccountReceivableManagement.integration.billing_data_acquisition.TimesheetsPendingApprovalException(
                        "Billing data acquisition pending: " + message);
            }
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing data acquisition failed: "
                            + (!message.isBlank() ? message : "no Billing Snapshot was returned."));
        }
        if (response.getData().getStatus() == BillingSnapshotStatus.CANCELLED) {
            // An existing snapshot of this period was reused, but it no longer bills anything.
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Snapshot " + response.getData().getSnapshotNumber() + " of this billing period is "
                            + "CANCELLED; it cannot be billed automatically.");
        }
        return response.getData().getSnapshotId();
    }

    /**
     * Occurrence-based billing: the amount is the occurrence's own. Fixed
     * Price, Milestone Plan (milestone / installment) and Recurring
     * occurrences are first checked by the service that generated them
     * (source configuration, matching payment entry or recurring term,
     * positive amount, currency, no overlapping period, contract value not
     * over-allocated), before anything is taxed. A due occurrence still
     * SCHEDULED (the occurrence scheduler runs separately) is moved to
     * TAX_PENDING through the existing locked, idempotent transition; one
     * that is already further along is left as it is.
     */
    private UUID prepareOccurrenceForTax(BillingSource source) {
        BillingSchedule occurrence = source.occurrence();
        log.info("[AutomaticBillingOccurrence] configuration={} occurrence={} type={} installment={} billingDate={} "
                        + "amount={} status={} taxStatus={} - eligible, preparing for tax.",
                MDC.get(MDC_CONFIGURATION_ID), occurrence.getBillingScheduleId(), source.billingType(),
                occurrence.getPeriodNumber(), occurrence.getBillingDate(), occurrence.getBillingAmount(),
                occurrence.getPeriodStatus(), occurrence.getTaxStatus());
        if (source.billingType() == BillingType.FIXED_PRICE) {
            billingOccurrenceService.requireFixedPriceOccurrenceBillable(occurrence.getBillingScheduleId());
        } else if (source.billingType() == BillingType.MILESTONE) {
            billingOccurrenceService.requireMilestonePlanOccurrenceBillable(occurrence.getBillingScheduleId());
        } else if (source.billingType() == BillingType.RETAINER) {
            billingOccurrenceService.requireRecurringOccurrenceBillable(occurrence.getBillingScheduleId());
        }
        if (occurrence.getPeriodStatus() == BillingPeriodStatus.SCHEDULED) {
            billingOccurrenceTransactionService.transitionSingleOccurrenceToTaxPending(occurrence);
        }
        return null;
    }

    // ================================================================ approval

    /**
     * Submits through the existing approval workflow. An invoice already in
     * the workflow is left there; a REJECTED invoice needs manual correction
     * and resubmission under the existing rules, so automation stops.
     */
    private InvoiceStatus submitForApproval(UUID invoiceId) {
        InvoiceResponseDto invoice = invoiceService.getInvoiceById(invoiceId);
        InvoiceStatus status = invoice.getStatus();

        if (status == InvoiceStatus.GENERATED) {
            return invoiceService.submitForApproval(invoiceId).getStatus();
        }
        if (status == InvoiceStatus.REJECTED) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Invoice " + invoice.getInvoiceNumber() + " was rejected; it must be corrected and "
                            + "resubmitted through the manual approval workflow.");
        }
        return status;
    }

    // ================================================================ helpers

    private UUID requireSnapshot(AutomaticBillingRun run) {
        if (run.getBillingSnapshotId() == null) {
            throw new IllegalStateException(
                    "Automatic billing run " + run.getAutomaticBillingRunId() + " has no Billing Snapshot.");
        }
        return run.getBillingSnapshotId();
    }

    /**
     * Renews this worker's claim, then marks the step in progress. Renewing
     * before every step is what tells an active run - even a slow one - from
     * an abandoned one: only a run whose worker stopped renewing can expire
     * and be taken over.
     */
    private void startStep(UUID runId, UUID claimToken, AutomaticBillingStep step) {
        automaticBillingRunService.renewClaim(runId, claimToken, claimDuration);
        automaticBillingRunService.startStep(runId, claimToken, step);
    }

    /**
     * Records the failure with the retry the policy allows: a temporary
     * failure with attempts left gets a backed-off {@code nextRetryAt}; a
     * permanent failure, or the last attempt, gets none and waits for manual
     * intervention.
     */
    private void recordFailure(UUID runId, UUID claimToken, RuntimeException ex) {
        String reason = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : ex.getClass().getSimpleName();
        // The message is sanitized like the stored reason: exception messages can echo
        // headers, tokens or connection strings. The stack trace (whose first line is the
        // raw message) is only logged at DEBUG - off in production.
        log.warn("[AutomaticBilling] run={} failed: {}: {}", runId, ex.getClass().getSimpleName(),
                AutomaticBillingRunServiceImpl.sanitizeFailureReason(reason));
        log.debug("[AutomaticBilling] run={} failure stack trace", runId, ex);
        try {
            int attemptCount = automaticBillingRunService.findById(runId)
                    .map(AutomaticBillingRun::getAttemptCount)
                    .orElse(Integer.MAX_VALUE);
            LocalDateTime nextRetryAt = retryPolicy.nextRetryAt(ex, attemptCount);
            AutomaticBillingRun failed = automaticBillingRunService.markFailed(runId, claimToken, reason, nextRetryAt);
            Metrics.counter("ar.automatic_billing.failures",
                    "step", String.valueOf(failed != null ? failed.getFailureStep() : null),
                    "retryable", String.valueOf(nextRetryAt != null)).increment();
            if (nextRetryAt == null) {
                log.warn("[AutomaticBilling] run={} will not be retried automatically ({} after attempt {}).",
                        runId, retryPolicy.isRetryable(ex) ? "attempts exhausted" : "not retryable",
                        attemptCount);
            }
        } catch (AutomaticBillingRunClaimLostException lost) {
            log.warn("[AutomaticBilling] run={} failed after losing its claim; failure not recorded: {}",
                    runId, lost.getMessage());
        }
    }

    static final String MDC_RUN_ID = "billingRunId";
    static final String MDC_CONFIGURATION_ID = "billingConfigurationId";
    static final String MDC_OCCURRENCE_ID = "billingOccurrenceId";
    static final String MDC_BILLING_TYPE = "billingType";
    static final String MDC_BILLING_PERIOD = "billingPeriod";

    /**
     * One line per processed run with everything needed to trace it:
     * identifiers, type, period, state, step, attempt, invoice and the
     * stored (already sanitized) failure reason. No invoice or client data.
     */
    private void logOutcome(AutomaticBillingRun run) {
        log.info("[AutomaticBillingRunOutcome] run={} configuration={} occurrence={} type={} period={} status={} "
                        + "lastCompletedStep={} failureStep={} attempt={} invoice={} nextRetryAt={} reason={}",
                run.getAutomaticBillingRunId(), MDC.get(MDC_CONFIGURATION_ID), MDC.get(MDC_OCCURRENCE_ID),
                MDC.get(MDC_BILLING_TYPE) != null ? MDC.get(MDC_BILLING_TYPE) : "-", MDC.get(MDC_BILLING_PERIOD),
                run.getStatus(), run.getLastCompletedStep(),
                run.getStatus() == AutomaticBillingRunStatus.FAILED ? run.getFailureStep() : null,
                run.getAttemptCount(), run.getInvoiceId(), run.getNextRetryAt(),
                run.getStatus() == AutomaticBillingRunStatus.FAILED ? run.getFailureReason() : null);
    }

    private AutomaticBillingRun loadRun(UUID runId) {
        return automaticBillingRunService.findById(runId)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                        "Automatic billing run could not be found."));
    }
}
