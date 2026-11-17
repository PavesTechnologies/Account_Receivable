package com.AccountReceivableManagement.service_Imple.automatic_billing;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType;
import com.AccountReceivableManagement.repo.automatic_billing.AutomaticBillingRunRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * Micrometer metrics of automatic billing, served by the existing Actuator
 * ({@code GET /paves/ar/actuator/metrics/<name>}, JWT required). No metrics
 * backend is assumed; a scraper can be added later without code changes.
 * <p>
 * State gauges - read from the database when the metric is requested:
 * <ul>
 *   <li>{@code ar.automatic_billing.runs.stuck} - PROCESSING runs whose claim
 *       expired (worker stopped); the scheduler recovers them;</li>
 *   <li>{@code ar.automatic_billing.runs.needs_intervention} - FAILED runs
 *       with no automatic retry left (business-data error or attempts
 *       exhausted);</li>
 *   <li>{@code ar.automatic_billing.runs.retry_scheduled} - FAILED runs
 *       waiting for their backed-off retry;</li>
 *   <li>{@code ar.automatic_billing.runs.pending_approval} - invoices handed
 *       to Finance Manager approval by automation;</li>
 *   <li>{@code ar.automatic_billing.occurrences.due_without_run} - due
 *       occurrences of automatic configurations not yet picked up (backlog).</li>
 * </ul>
 * Activity - recorded as it happens: {@code ar.automatic_billing.tick}
 * (timer), {@code ar.automatic_billing.runs.processed} (counter, tag
 * {@code outcome}), {@code ar.automatic_billing.failures} (counter, tags
 * {@code step}, {@code retryable}), and - elsewhere - {@code ar.tms.request.failures},
 * {@code ar.invoice.document.failures}, {@code ar.invoice.delivery.failures}.
 */
@Slf4j
@Component
public class AutomaticBillingMetrics {

    private final MeterRegistry registry;
    private final AutomaticBillingRunRepository runRepository;
    private final BillingScheduleRepository scheduleRepository;
    private final Clock clock;
    private final Timer tickTimer;

    public AutomaticBillingMetrics(MeterRegistry registry, AutomaticBillingRunRepository runRepository,
            BillingScheduleRepository scheduleRepository) {
        this.registry = registry;
        this.runRepository = runRepository;
        this.scheduleRepository = scheduleRepository;
        this.clock = Clock.systemDefaultZone();
        this.tickTimer = Timer.builder("ar.automatic_billing.tick")
                .description("Duration of one automatic billing scheduler pass")
                .register(registry);
    }

    @PostConstruct
    void registerGauges() {
        gauge("ar.automatic_billing.runs.stuck", "PROCESSING runs whose claim has expired",
                () -> runRepository.countByStatusAndClaimExpiresAtBefore(
                        AutomaticBillingRunStatus.PROCESSING, LocalDateTime.now(clock)));
        gauge("ar.automatic_billing.runs.needs_intervention", "FAILED runs with no automatic retry left",
                () -> runRepository.countByStatusAndNextRetryAtIsNull(AutomaticBillingRunStatus.FAILED));
        gauge("ar.automatic_billing.runs.retry_scheduled", "FAILED runs waiting for a scheduled retry",
                () -> runRepository.countByStatusAndNextRetryAtIsNotNull(AutomaticBillingRunStatus.FAILED));
        gauge("ar.automatic_billing.runs.pending_approval", "Automatically created invoices awaiting approval",
                () -> runRepository.countByStatus(AutomaticBillingRunStatus.PENDING_APPROVAL));
        gauge("ar.automatic_billing.occurrences.due_without_run",
                "Due occurrences of automatic configurations not yet picked up",
                () -> scheduleRepository.countDueOccurrencesWithoutAutomaticRun(LocalDate.now(clock),
                        ApprovalStatus.APPROVED, InvoiceGenerationType.AUTOMATIC, BillingSchedule.UNBILLED_STATUSES));
    }

    /** Records one scheduler pass: its duration and every run outcome it produced. */
    void recordTick(AutomaticBillingScheduler.TickSummary summary) {
        tickTimer.record(Duration.ofMillis(summary.durationMillis));
        for (AutomaticBillingRunStatus status : AutomaticBillingRunStatus.values()) {
            int count = summary.outcome(status);
            if (count > 0) {
                registry.counter("ar.automatic_billing.runs.processed", "outcome", status.name()).increment(count);
            }
        }
        if (summary.errors() > 0) {
            registry.counter("ar.automatic_billing.runs.processed", "outcome", "ERROR").increment(summary.errors());
        }
    }

    private void gauge(String name, String description, Supplier<Long> count) {
        Gauge.builder(name, () -> {
                    try {
                        return count.get();
                    } catch (RuntimeException ex) {
                        // Database unavailable: report "unknown", never fail the metrics request.
                        log.debug("[AutomaticBillingMetrics] {} unavailable: {}", name, ex.getMessage());
                        return Double.NaN;
                    }
                })
                .description(description)
                .register(registry);
    }
}
