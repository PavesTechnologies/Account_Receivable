package com.AccountReceivableManagement.entity.project_entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read-only mirror of PMS project_milestones, maintained by CDC.
 * PMS is the source of truth; AR must not edit these rows.
 * Amounts are intentionally absent: AR derives them from its own contract value
 * and the synchronised allocation percentage.
 */
@Entity
@Table(name = "project_milestone_reference", indexes = {
        @Index(name = "idx_pmr_project", columnList = "pms_project_id"),
        @Index(name = "idx_pmr_project_readiness", columnList = "pms_project_id, billing_readiness")
})
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class ProjectMilestoneReference {

    @Id
    @Column(name = "pms_milestone_id")
    private Long pmsMilestoneId;

    @Column(name = "pms_project_id")
    private Long pmsProjectId;

    @Column(name = "milestone_code", length = 50)
    private String milestoneCode;

    @Column(name = "milestone_name", length = 200)
    private String milestoneName;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "pms_epic_id")
    private Long pmsEpicId;

    @Column(name = "planned_start_date")
    private LocalDate plannedStartDate;

    @Column(name = "planned_due_date")
    private LocalDate plannedDueDate;

    @Column(name = "actual_completion_date")
    private LocalDate actualCompletionDate;

    /** PMS MilestoneExecutionStatus name; kept as text so new PMS values never break sync. */
    @Column(name = "execution_status", length = 20)
    private String executionStatus;

    @Column(name = "billable")
    private Boolean billable;

    @Column(name = "allocation_percentage", precision = 5, scale = 2)
    private BigDecimal allocationPercentage;

    /** PMS MilestoneBillingReadiness name: NOT_BILLABLE, NOT_READY, READY_FOR_BILLING. */
    @Column(name = "billing_readiness", length = 30)
    private String billingReadiness;

    /** PMS optimistic-lock version; used to discard stale or replayed CDC events. */
    @Column(name = "source_version")
    private Long sourceVersion;

    /** True once PMS deleted the milestone; the row is kept for billing traceability. */
    @Builder.Default
    @Column(name = "source_deleted", nullable = false)
    private Boolean sourceDeleted = Boolean.FALSE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "changed_at")
    private LocalDateTime changedAt;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;
}
