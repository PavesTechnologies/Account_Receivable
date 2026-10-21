package com.AccountReceivableManagement.service_Imple.automatic_billing;

import com.AccountReceivableManagement.config.properties.AutomaticBillingSchedulerProperties;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingPeriodDto;
import com.AccountReceivableManagement.entity.automatic_billing.AutomaticBillingRun;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType;
import com.AccountReceivableManagement.repo.automatic_billing.AutomaticBillingRunRepository;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.service_Imple.projectbilling_config.BillingOccurrenceServiceImpl;
import com.AccountReceivableManagement.service_interface.automatic_billing.AutomaticBillingRunService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Finds billing events that are due for automatic billing and hands each one
 * to {@link AutomaticBillingOrchestrator}. Holds no billing logic: periods
 * come from the Billing Occurrences (or, for Time &amp; Material, from
 * {@code BillingOccurrenceServiceImpl.resolveTimeAndMaterialBillingPeriods});
 * runs are created by {@link AutomaticBillingRunService} (unique keys make
 * that safe across workers and instances) and claimed by the orchestrator
 * (atomic claim), so this class never claims anything itself.
 * <p>
 * Each tick handles at most {@code ar.automatic-billing.batch-size} billing
 * events, in this order:
 * <ol>
 *   <li>runs still SCHEDULED - created earlier but never processed (e.g. the
 *       instance stopped right after creating them);</li>
 *   <li>recovery ({@code ar.automatic-billing.retry.enabled}): interrupted
 *       runs - PROCESSING with an expired claim, i.e. their worker stopped
 *       renewing it - are taken over and resumed, or, with no attempt left,
 *       marked FAILED; then FAILED runs whose backed-off {@code nextRetryAt}
 *       has arrived are retried. A run without {@code nextRetryAt}
 *       (permanent failure, attempts exhausted) is never picked up;</li>
 *   <li>due occurrences of approved AUTOMATIC configurations without a run;</li>
 *   <li>due Time &amp; Material periods of approved AUTOMATIC configurations
 *       without a run.</li>
 * </ol>
 * Steps 3 and 4 include billing events whose billing date passed without
 * being processed (backfill), oldest first, within
 * {@code ar.automatic-billing.backfill.*}. PENDING_APPROVAL, COMPLETED and
 * actively claimed PROCESSING runs are never picked up. Every run is claimed
 * atomically by the orchestrator, so retry, recovery and new work can never
 * process the same billing event twice, on this or another instance. Each
 * billing event is processed on its own; one failing does not stop the rest.
 * No transaction is held around the batch - each lookup, run creation and
 * orchestrator step commits on its own.
 * <p>
 * Independent of {@link com.AccountReceivableManagement.service_Imple.projectbilling_config.BillingOccurrenceStatusScheduler}:
 * that one moves due occurrences SCHEDULED -&gt; TAX_PENDING for the manual
 * flow; this one picks up SCHEDULED, TAX_PENDING and TAX_CALCULATED due
 * occurrences alike, and the orchestrator performs the same locked,
 * idempotent transition itself when it finds one still SCHEDULED. The two
 * can run in either order.
 */
@Slf4j
@Component
public class AutomaticBillingScheduler {

    private final AutomaticBillingSchedulerProperties properties;
    private final AutomaticBillingOrchestrator orchestrator;
    private final AutomaticBillingRunService automaticBillingRunService;
    private final AutomaticBillingRunRepository automaticBillingRunRepository;
    private final BillingScheduleRepository billingScheduleRepository;
    private final BillingConfigurationRepository billingConfigurationRepository;
    private final BillingSnapshotRepository billingSnapshotRepository;
    private final BillingOccurrenceServiceImpl billingOccurrenceService;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    /** Optional: metrics are recorded when the Micrometer-backed bean is present. */
    private AutomaticBillingMetrics metrics;

    @Autowired(required = false)
    void setMetrics(AutomaticBillingMetrics metrics) {
        this.metrics = metrics;
    }

    /** Reconciles runs whose invoice was approved but whose completion was not recorded (G-18). */
    private com.AccountReceivableManagement.service_interface.automatic_billing.AutomaticBillingRunRecoveryService
            recoveryService;

    @Autowired(required = false)
    void setRecoveryService(
            com.AccountReceivableManagement.service_interface.automatic_billing.AutomaticBillingRunRecoveryService
                    recoveryService) {
        this.recoveryService = recoveryService;
    }

    @Autowired
    public AutomaticBillingScheduler(AutomaticBillingSchedulerProperties properties,
            AutomaticBillingOrchestrator orchestrator,
            AutomaticBillingRunService automaticBillingRunService,
            AutomaticBillingRunRepository automaticBillingRunRepository,
            BillingScheduleRepository billingScheduleRepository,
            BillingConfigurationRepository billingConfigurationRepository,
            BillingSnapshotRepository billingSnapshotRepository,
            BillingOccurrenceServiceImpl billingOccurrenceService,
            PlatformTransactionManager transactionManager) {
        this(properties, orchestrator, automaticBillingRunService, automaticBillingRunRepository,
                billingScheduleRepository, billingConfigurationRepository, billingSnapshotRepository,
                billingOccurrenceService, transactionManager, Clock.systemDefaultZone());
    }

    AutomaticBillingScheduler(AutomaticBillingSchedulerProperties properties,
            AutomaticBillingOrchestrator orchestrator,
            AutomaticBillingRunService automaticBillingRunService,
            AutomaticBillingRunRepository automaticBillingRunRepository,
            BillingScheduleRepository billingScheduleRepository,
            BillingConfigurationRepository billingConfigurationRepository,
            BillingSnapshotRepository billingSnapshotRepository,
            BillingOccurrenceServiceImpl billingOccurrenceService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.properties = properties;
        this.orchestrator = orchestrator;
        this.automaticBillingRunService = automaticBillingRunService;
        this.automaticBillingRunRepository = automaticBillingRunRepository;
        this.billingScheduleRepository = billingScheduleRepository;
        this.billingConfigurationRepository = billingConfigurationRepository;
        this.billingSnapshotRepository = billingSnapshotRepository;
        this.billingOccurrenceService = billingOccurrenceService;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    @PostConstruct
    void logConfiguration() {
        if (properties.isEnabled()) {
            log.info("[AutomaticBillingScheduler] enabled - cron '{}', batch size {}.",
                    properties.getCron(), properties.getBatchSize());
        } else {
            log.info("[AutomaticBillingScheduler] disabled (ar.automatic-billing.enabled=false); "
                    + "no automatic billing will be processed.");
        }
    }

    @Scheduled(cron = "${ar.automatic-billing.cron:" + AutomaticBillingSchedulerProperties.DEFAULT_CRON + "}")
    public void scheduledTick() {
        try {
            runOnce();
        } catch (RuntimeException ex) {
            // Discovery itself failed (e.g. database unavailable); the next tick tries again.
            log.error("[AutomaticBillingScheduler] tick aborted: {}",
                    AutomaticBillingRunServiceImpl.sanitizeFailureReason(ex.getMessage()), ex);
        }
    }

    /** One scheduler pass. Returns what it did; does nothing at all while disabled. */
    public TickSummary runOnce() {
        if (!properties.isEnabled()) {
            log.debug("[AutomaticBillingScheduler] disabled; tick skipped.");
            return new TickSummary();
        }

        long startedAt = System.nanoTime();
        LocalDate today = LocalDate.now(clock);
        int batchSize = properties.getBatchSize();
        TickSummary summary = new TickSummary();

        // G-18: runs whose invoice Finance already approved, but whose
        // after-commit completion was lost. Records a decision already made -
        // not a retry - so it runs whatever the retry policy, outside the batch budget.
        if (recoveryService != null) {
            try {
                summary.reconciled = recoveryService.reconcileApprovedRuns(batchSize);
            } catch (RuntimeException ex) {
                summary.errors++;
                log.warn("[AutomaticBillingScheduler] approval reconciliation failed: {}",
                        AutomaticBillingRunServiceImpl.sanitizeFailureReason(ex.getMessage()));
            }
        }

        List<UUID> pendingRunIds = automaticBillingRunRepository.findRunIdsByStatus(
                AutomaticBillingRunStatus.SCHEDULED, PageRequest.of(0, batchSize));
        for (UUID runId : pendingRunIds) {
            summary.resumed++;
            process(summary, "run " + runId, () -> runId);
        }

        int remaining = batchSize - pendingRunIds.size();
        if (remaining > 0) {
            remaining -= properties.getRetry().isEnabled()
                    ? recover(summary, remaining)
                    : recoverWithoutAutomaticRetry(summary, remaining);
        }

        LocalDate earliestBillingDate = earliestBillingDate(today);
        if (remaining > 0) {
            List<UUID> occurrenceIds = billingScheduleRepository.findDueOccurrenceIdsWithoutAutomaticRun(
                    today, earliestBillingDate, ApprovalStatus.APPROVED, InvoiceGenerationType.AUTOMATIC,
                    BillingSchedule.UNBILLED_STATUSES, PageRequest.of(0, remaining));
            for (UUID occurrenceId : occurrenceIds) {
                summary.newOccurrences++;
                process(summary, "occurrence " + occurrenceId, () -> automaticBillingRunService
                        .findOrCreateForOccurrence(occurrenceId).getAutomaticBillingRunId());
            }
            remaining -= occurrenceIds.size();
        }

        if (remaining > 0) {
            for (DueTimeAndMaterialPeriod period : findDueTimeAndMaterialPeriods(today, earliestBillingDate,
                    remaining, summary)) {
                summary.newTimeAndMaterialPeriods++;
                process(summary, "T&M period " + period, () -> automaticBillingRunService
                        .findOrCreateForTimeAndMaterialPeriod(period.configurationId(), period.start(), period.end())
                        .getAutomaticBillingRunId());
            }
        }

        summary.durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
        if (metrics != null) {
            metrics.recordTick(summary);
        }
        if (summary.handled() > 0 || summary.errors > 0) {
            log.info("[AutomaticBillingScheduler] tick done in {} ms - {}", summary.durationMillis, summary);
        } else {
            log.debug("[AutomaticBillingScheduler] tick done in {} ms - nothing due.", summary.durationMillis);
        }
        return summary;
    }

    /**
     * Creates/reuses the run and processes it, isolated from every other
     * billing event of the batch. A step failure has already been recorded on
     * the run by the orchestrator; anything thrown here (e.g. run creation
     * rejected) is logged and the batch continues.
     */
    private void process(TickSummary summary, String what, Supplier<UUID> runId) {
        UUID id = null;
        try {
            id = runId.get();
            AutomaticBillingRun run = orchestrator.processRun(id);
            summary.count(run.getStatus());
            if (run.getStatus() == AutomaticBillingRunStatus.FAILED) {
                log.warn("[AutomaticBillingScheduler] {} (run {}) failed at {}: {}", what, id,
                        run.getFailureStep(), run.getFailureReason());
            } else {
                log.debug("[AutomaticBillingScheduler] {} (run {}) -> {}", what, id, run.getStatus());
            }
        } catch (RuntimeException ex) {
            summary.errors++;
            log.warn("[AutomaticBillingScheduler] {} (run {}) could not be processed: {}", what, id,
                    AutomaticBillingRunServiceImpl.sanitizeFailureReason(ex.getMessage()));
        }
    }

    // ================================================================ Time & Material discovery

    /** Lower bound passed to discovery when the backfill window is unlimited. */
    static final LocalDate UNBOUNDED_EARLIEST_BILLING_DATE = LocalDate.of(1900, 1, 1);

    /**
     * Oldest billing date discovery picks up: today only when backfill is
     * off, otherwise {@code maxAgeDays} back (0 = no limit).
     */
    LocalDate earliestBillingDate(LocalDate today) {
        AutomaticBillingSchedulerProperties.Backfill backfill = properties.getBackfill();
        if (!backfill.isEnabled()) {
            return today;
        }
        return backfill.getMaxAgeDays() > 0 ? today.minusDays(backfill.getMaxAgeDays())
                : UNBOUNDED_EARLIEST_BILLING_DATE;
    }

    // ================================================================ recovery

    /**
     * Interrupted runs first (they hold a billing event mid-way), then due
     * retries; at most {@code limit} together. Returns how many were handled.
     */
    private int recover(TickSummary summary, int limit) {
        LocalDateTime now = LocalDateTime.now(clock);
        int maxAttempts = properties.getRetry().getMaxAttempts();
        int handled = 0;

        for (UUID runId : automaticBillingRunRepository.findInterruptedRunIds(now, PageRequest.of(0, limit))) {
            handled++;
            AutomaticBillingRun run = automaticBillingRunService.findById(runId).orElse(null);
            if (run == null) {
                continue;
            }
            if (run.getAttemptCount() != null && run.getAttemptCount() >= maxAttempts) {
                try {
                    automaticBillingRunService.failInterruptedRun(runId, "Interrupted during "
                            + run.getCurrentStep() + " on attempt " + run.getAttemptCount() + " of " + maxAttempts
                            + "; no automatic attempt left.").ifPresent(failed -> summary.abandoned++);
                } catch (RuntimeException ex) {
                    summary.errors++;
                    log.warn("[AutomaticBillingScheduler] interrupted run {} could not be closed: {}", runId,
                            AutomaticBillingRunServiceImpl.sanitizeFailureReason(ex.getMessage()));
                }
                continue;
            }
            summary.recovered++;
            log.info("[AutomaticBillingScheduler] run {} was interrupted at {} (attempt {}); resuming.", runId,
                    run.getCurrentStep(), run.getAttemptCount());
            process(summary, "interrupted run " + runId, () -> runId);
        }

        if (handled < limit) {
            for (UUID runId : automaticBillingRunRepository.findRunIdsDueForRetry(now,
                    PageRequest.of(0, limit - handled))) {
                handled++;
                summary.retried++;
                process(summary, "retry of run " + runId, () -> runId);
            }
        }
        return handled;
    }

    /**
     * G-30: with automatic retry disabled nothing is attempted again on its
     * own, but nothing is left stranded either. An interrupted run is closed
     * as FAILED (failure recorded, no automatic retry) so it shows as needing
     * intervention instead of staying PROCESSING forever; runs explicitly
     * re-queued since their last failure - by an operator or a configuration
     * re-approval - are processed, as those are decisions, not retries.
     */
    private int recoverWithoutAutomaticRetry(TickSummary summary, int limit) {
        LocalDateTime now = LocalDateTime.now(clock);
        int handled = 0;

        for (UUID runId : automaticBillingRunRepository.findInterruptedRunIds(now, PageRequest.of(0, limit))) {
            handled++;
            AutomaticBillingRun run = automaticBillingRunService.findById(runId).orElse(null);
            if (run == null) {
                continue;
            }
            try {
                automaticBillingRunService.failInterruptedRun(runId, "Interrupted during "
                        + run.getCurrentStep() + " on attempt " + run.getAttemptCount()
                        + "; automatic retry is disabled - re-queue it once the cause is understood.")
                        .ifPresent(failed -> summary.abandoned++);
            } catch (RuntimeException ex) {
                summary.errors++;
                log.warn("[AutomaticBillingScheduler] interrupted run {} could not be closed: {}", runId,
                        AutomaticBillingRunServiceImpl.sanitizeFailureReason(ex.getMessage()));
            }
        }

        if (handled < limit) {
            for (UUID runId : automaticBillingRunRepository.findRequeuedRunIdsDue(now,
                    PageRequest.of(0, limit - handled))) {
                handled++;
                summary.retried++;
                process(summary, "re-queued run " + runId, () -> runId);
            }
        }
        return handled;
    }

    record DueTimeAndMaterialPeriod(UUID configurationId, LocalDate start, LocalDate end) {
        @Override
        public String toString() {
            return start + ".." + end + " of configuration " + configurationId;
        }
    }

    /**
     * Due T&amp;M periods without a run, up to {@code limit}. Periods come from
     * the configuration's own billing frequency via the Phase 2 resolver -
     * never computed here. A period that any Billing Snapshot of the project
     * already overlaps is left to the manual flow: its timesheets are being,
     * or have been, billed there.
     */
    private List<DueTimeAndMaterialPeriod> findDueTimeAndMaterialPeriods(LocalDate today,
            LocalDate earliestBillingDate, int limit, TickSummary summary) {
        List<DueTimeAndMaterialPeriod> due = new ArrayList<>();
        List<UUID> configurationIds = billingConfigurationRepository.findAutomaticConfigurationIdsByBillingTypeNames(
                ApprovalStatus.APPROVED, InvoiceGenerationType.AUTOMATIC,
                BillingOccurrenceServiceImpl.TIME_AND_MATERIAL_BILLING_TYPE_NAMES);

        for (UUID configurationId : configurationIds) {
            if (due.size() >= limit) {
                break;
            }
            try {
                due.addAll(readOnly.execute(status -> duePeriodsOf(configurationId, today, earliestBillingDate,
                        limit - due.size())));
            } catch (RuntimeException ex) {
                summary.errors++;
                log.warn("[AutomaticBillingScheduler] billing periods of T&M configuration {} could not be "
                                + "resolved: {}", configurationId,
                        AutomaticBillingRunServiceImpl.sanitizeFailureReason(ex.getMessage()));
            }
        }
        return due;
    }

    private List<DueTimeAndMaterialPeriod> duePeriodsOf(UUID configurationId, LocalDate today,
            LocalDate earliestBillingDate, int limit) {
        BillingConfiguration configuration = billingConfigurationRepository.findById(configurationId).orElse(null);
        if (configuration == null || configuration.getProject() == null) {
            return List.of();
        }
        Long projectId = configuration.getProject().getPmsProjectId();

        List<DueTimeAndMaterialPeriod> due = new ArrayList<>();
        for (BillingPeriodDto period : billingOccurrenceService.resolveTimeAndMaterialBillingPeriods(configurationId, today)) {
            if (due.size() >= limit) {
                break;
            }
            if (period.getBillingDate().isAfter(today) || period.getBillingDate().isBefore(earliestBillingDate)) {
                continue;
            }
            Optional<AutomaticBillingRun> existingRunOpt = automaticBillingRunRepository
                    .findByBillingConfiguration_BillingConfigurationIdAndBillingPeriodStartAndBillingPeriodEnd(
                            configurationId, period.getPeriodStartDate(), period.getPeriodEndDate());
            if (existingRunOpt.isPresent()) {
                AutomaticBillingRun existingRun = existingRunOpt.get();
                boolean isTerminalOrPending = existingRun.getStatus() == AutomaticBillingRunStatus.COMPLETED
                        || existingRun.getStatus() == AutomaticBillingRunStatus.PENDING_APPROVAL
                        || existingRun.getStatus() == AutomaticBillingRunStatus.PROCESSING
                        || existingRun.getStatus() == AutomaticBillingRunStatus.SCHEDULED
                        || existingRun.getNextRetryAt() != null
                        || existingRun.getInvoiceId() != null
                        || existingRun.getBillingSnapshotId() != null;
                if (isTerminalOrPending) {
                    continue;
                }
                // G-14: If a previous run failed during source acquisition with no financial records created
                // (e.g. zero hours or unapproved timesheets), it should not be permanently lost.
                // It can be rediscovered on subsequent days if late-approved timesheets become available.
                boolean attemptedToday = existingRun.getUpdatedAt() == null
                        || !existingRun.getUpdatedAt().toLocalDate().isBefore(today);
                if (attemptedToday) {
                    continue;
                }
            }
            boolean alreadyAcquired = billingSnapshotRepository
                    .existsByProjectIdAndBillingPeriodStartLessThanEqualAndBillingPeriodEndGreaterThanEqual(
                            projectId, period.getPeriodEndDate(), period.getPeriodStartDate());
            if (alreadyAcquired) {
                continue;
            }
            due.add(new DueTimeAndMaterialPeriod(configurationId, period.getPeriodStartDate(),
                    period.getPeriodEndDate()));
        }
        return due;
    }

    // ================================================================ summary

    /** What one tick did - logged, and returned for tests and diagnostics. */
    public static final class TickSummary {

        int resumed;
        int recovered;
        int retried;
        int abandoned;
        int reconciled;
        int newOccurrences;
        int newTimeAndMaterialPeriods;
        int errors;
        long durationMillis;
        private final Map<AutomaticBillingRunStatus, Integer> outcomes = new ConcurrentHashMap<>();

        void count(AutomaticBillingRunStatus status) {
            outcomes.merge(status, 1, Integer::sum);
        }

        public int handled() {
            return resumed + recovered + retried + abandoned + reconciled + newOccurrences + newTimeAndMaterialPeriods;
        }

        /** Interrupted runs taken over and resumed. */
        public int recovered() {
            return recovered;
        }

        /** FAILED runs retried because their {@code nextRetryAt} arrived. */
        public int retried() {
            return retried;
        }

        /** PENDING_APPROVAL runs completed from their already-approved invoice (G-18). */
        public int reconciled() {
            return reconciled;
        }

        /** Interrupted runs closed as FAILED - no automatic attempt left. */
        public int abandoned() {
            return abandoned;
        }

        public int resumed() {
            return resumed;
        }

        public int newOccurrences() {
            return newOccurrences;
        }

        public int newTimeAndMaterialPeriods() {
            return newTimeAndMaterialPeriods;
        }

        public int errors() {
            return errors;
        }

        public int outcome(AutomaticBillingRunStatus status) {
            return outcomes.getOrDefault(status, 0);
        }

        @Override
        public String toString() {
            return "resumed runs " + resumed + ", recovered " + recovered + ", retried " + retried
                    + ", abandoned " + abandoned + ", reconciled " + reconciled + ", new occurrences " + newOccurrences
                    + ", new T&M periods " + newTimeAndMaterialPeriods + ", outcomes " + outcomes
                    + ", errors " + errors;
        }
    }
}
