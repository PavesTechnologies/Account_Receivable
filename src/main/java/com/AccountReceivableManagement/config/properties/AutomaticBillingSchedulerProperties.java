package com.AccountReceivableManagement.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * {@code ar.automatic-billing.*} settings of AutomaticBillingScheduler.
 * Validated at startup: an invalid cron, batch size, retry or backfill
 * setting stops the application with the offending property named, instead
 * of failing silently later. The claim duration
 * ({@code ar.automatic-billing.claim-duration}) is read by
 * AutomaticBillingOrchestrator itself and is not repeated here.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ar.automatic-billing")
public class AutomaticBillingSchedulerProperties {

    /** Default polling schedule - also the fallback of the {@code @Scheduled} placeholder. */
    public static final String DEFAULT_CRON = "0 */5 * * * *";

    /** Off unless explicitly enabled; when off, a tick returns without touching the database. */
    private boolean enabled = false;

    /** Spring 6-field cron (second minute hour day month weekday). */
    @NotBlank
    private String cron = DEFAULT_CRON;

    /** Maximum billing events (new, resumed and retried together) handed to the orchestrator per tick. */
    @Min(1)
    @Max(500)
    private int batchSize = 50;

    @Valid
    @NotNull
    private Retry retry = new Retry();

    @Valid
    @NotNull
    private Backfill backfill = new Backfill();

    @AssertTrue(message = "ar.automatic-billing.cron must be a valid 6-field Spring cron expression, "
            + "e.g. '0 */5 * * * *'")
    public boolean isCronValid() {
        return cron != null && CronExpression.isValidExpression(cron);
    }

    /**
     * {@code ar.automatic-billing.retry.*}: bounded, backed-off retry of runs
     * that failed for a temporary reason, and recovery of interrupted runs.
     * The n-th retry waits {@code initialDelay * multiplier^(n-1)}, capped at
     * {@code maxDelay}; after {@code maxAttempts} attempts in total a run is
     * left FAILED for manual intervention.
     */
    @Getter
    @Setter
    public static class Retry {

        /** When off, FAILED and interrupted runs are never picked up by the scheduler. */
        private boolean enabled = true;

        /** Attempts in total, the first one included. */
        @Min(1)
        @Max(20)
        private int maxAttempts = 3;

        @NotNull
        private Duration initialDelay = Duration.ofMinutes(15);

        @DecimalMin("1.0")
        @DecimalMax("10.0")
        private double multiplier = 2.0;

        @NotNull
        private Duration maxDelay = Duration.ofHours(6);

        @AssertTrue(message = "ar.automatic-billing.retry.initial-delay must be positive and not longer than "
                + "ar.automatic-billing.retry.max-delay")
        public boolean isDelayValid() {
            return initialDelay != null && maxDelay != null && !initialDelay.isNegative() && !initialDelay.isZero()
                    && initialDelay.compareTo(maxDelay) <= 0;
        }
    }

    /**
     * {@code ar.automatic-billing.backfill.*}: billing events whose billing
     * date has passed without being processed (downtime, a disabled
     * scheduler) - picked up oldest first, through the same discovery as
     * events due today.
     */
    @Getter
    @Setter
    public static class Backfill {

        /** When off, only billing events whose billing date is today are picked up. */
        private boolean enabled = true;

        /**
         * Oldest billing date, in days before today, that is still picked up
         * automatically; older events are left to the manual flow. 0 = no
         * limit.
         */
        @Min(0)
        @Max(3660)
        private int maxAgeDays = 0;
    }
}
