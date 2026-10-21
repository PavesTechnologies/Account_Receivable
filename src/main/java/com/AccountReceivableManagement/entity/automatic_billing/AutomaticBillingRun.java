package com.AccountReceivableManagement.entity.automatic_billing;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingStep;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Automation execution/audit record for one billing event: one row per
 * (Billing Configuration, billing period) for as long as automation works on
 * it, across every attempt. The billing event itself stays the Billing
 * Occurrence ({@code BillingSchedule}) - or, for Time &amp; Material, which has
 * no persisted occurrence, the period resolved from the configuration by
 * {@code BillingOccurrenceServiceImpl.resolveTimeAndMaterialBillingPeriods}.
 * <p>
 * One common model for every billing type: {@code billingScheduleId} is set
 * for occurrence-based billing (Fixed Price, Milestone/Installment,
 * Recurring) and {@code null} for T&amp;M, whose {@code billingSnapshotId} is
 * set once its data is acquired. The billing type is not copied here - it
 * follows from the configuration, and whether the run is occurrence-based
 * from {@code billingScheduleId}.
 * <p>
 * Uniqueness: {@code uk_automatic_billing_run_period} mirrors
 * {@code uk_billing_schedule_period} / {@code uk_billing_acq_config_period}
 * (configuration + period), so the same billing event can never get a
 * second run, whichever worker or scheduler pass creates it.
 * {@code billing_schedule_id} is also unique.
 * <p>
 * {@code billingScheduleId}, {@code billingSnapshotId} and {@code invoiceId}
 * are plain id columns (as {@code Invoice.billingSnapshotId} and
 * {@code BillingAcquisition.snapshotId} are): configuration reconciliation
 * hard-deletes SCHEDULED occurrences, which a foreign key would block.
 */
@Entity
@Table(
        name = "automatic_billing_run",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_automatic_billing_run_period",
                        columnNames = {"billing_configuration_id", "billing_period_start", "billing_period_end"}
                ),
                @UniqueConstraint(
                        name = "uk_automatic_billing_run_schedule",
                        columnNames = {"billing_schedule_id"}
                )
        },
        indexes = {
                @Index(name = "idx_automatic_billing_run_status", columnList = "status, next_retry_at"),
                @Index(name = "idx_automatic_billing_run_invoice", columnList = "invoice_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutomaticBillingRun {

    /** Column length of {@link #failureReason}. */
    public static final int FAILURE_REASON_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "automatic_billing_run_id")
    private UUID automaticBillingRunId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "billing_configuration_id",
            referencedColumnName = "billing_configuration_id",
            nullable = false,
            updatable = false
    )
    private BillingConfiguration billingConfiguration;

    /** The Billing Occurrence processed; {@code null} for Time &amp; Material. */
    @Column(name = "billing_schedule_id", updatable = false)
    private UUID billingScheduleId;

    @Column(name = "billing_period_start", nullable = false, updatable = false)
    private LocalDate billingPeriodStart;

    @Column(name = "billing_period_end", nullable = false, updatable = false)
    private LocalDate billingPeriodEnd;

    /** T&amp;M source data, set when acquisition completes. */
    @Column(name = "billing_snapshot_id")
    private UUID billingSnapshotId;

    /** Set when the invoice draft is created (or found). */
    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private AutomaticBillingRunStatus status = AutomaticBillingRunStatus.SCHEDULED;

    /** Step being executed (PROCESSING) or that failed (FAILED); {@code null} otherwise. */
    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", length = 40)
    private AutomaticBillingStep currentStep;

    /** Last step that completed successfully - where a resumed attempt continues from. */
    @Enumerated(EnumType.STRING)
    @Column(name = "last_completed_step", length = 40)
    private AutomaticBillingStep lastCompletedStep;

    /** Number of processing attempts started (incremented on claim, never on a read). */
    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    /**
     * Identifies the worker that owns the current attempt; every state update
     * of a PROCESSING run must present it. Cleared when the attempt ends.
     */
    @Column(name = "claim_token")
    private UUID claimToken;

    /** The current claim may be taken over by another worker after this instant. */
    @Column(name = "claim_expires_at")
    private LocalDateTime claimExpiresAt;

    /** Most recent failure - kept after a later successful attempt, for audit. */
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_step", length = 40)
    private AutomaticBillingStep failureStep;

    /** Sanitized: never carries credentials or tokens. */
    @Column(name = "failure_reason", length = FAILURE_REASON_MAX_LENGTH)
    private String failureReason;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    /** Earliest time a FAILED run may be claimed again (set by the retry policy of a later phase). */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /** Start of the most recent attempt. */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = AutomaticBillingRunStatus.SCHEDULED;
        }
        if (attemptCount == null) {
            attemptCount = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isOccurrenceBased() {
        return billingScheduleId != null;
    }
}
