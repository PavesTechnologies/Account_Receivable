package com.AccountReceivableManagement.entity.automatic_billing;

import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunEventType;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingStep;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only audit of an operational action on an automatic billing run
 * (G-17/G-18). The run itself keeps only its latest failure; each event
 * keeps the failure that was in place when the action was taken, so a later
 * attempt never erases it, plus who acted and why. {@code runId} is a plain
 * reference, as for the other history tables.
 */
@Entity
@Table(
        name = "automatic_billing_run_event",
        indexes = @Index(name = "idx_automatic_billing_run_event_run", columnList = "run_id, occurred_at")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutomaticBillingRunEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "automatic_billing_run_event_id")
    private UUID automaticBillingRunEventId;

    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30, updatable = false)
    private AutomaticBillingRunEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_before", nullable = false, length = 30, updatable = false)
    private AutomaticBillingRunStatus statusBefore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_after", nullable = false, length = 30, updatable = false)
    private AutomaticBillingRunStatus statusAfter;

    /** The run's failure when the action was taken (null if it had none). */
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_step", length = 40, updatable = false)
    private AutomaticBillingStep failureStep;

    @Column(name = "failure_reason", length = AutomaticBillingRun.FAILURE_REASON_MAX_LENGTH, updatable = false)
    private String failureReason;

    @Column(name = "failed_at", updatable = false)
    private LocalDateTime failedAt;

    @Column(name = "attempt_count", updatable = false)
    private Integer attemptCount;

    /** Readable actor: the operator's UMS label, or SYSTEM. */
    @Column(name = "actor", nullable = false, length = 100, updatable = false)
    private String actor;

    /** The operator's stable UMS user id; null for the system. */
    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "reason", length = 500, updatable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    void prePersist() {
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
    }
}
