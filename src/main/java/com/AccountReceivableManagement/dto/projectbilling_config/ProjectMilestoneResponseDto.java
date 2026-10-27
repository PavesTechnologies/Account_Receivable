package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.MilestoneBillingState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A PMS project milestone as synchronised into AR (project_milestone_reference). Read-only: PMS is the source of
 * truth. {@code pmsMilestoneId} is the PMS id (a number) and is the only identifier a billing plan entry should ever
 * reference. Deleted milestones are never returned, so there is no deleted flag.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMilestoneResponseDto {

    private Long pmsMilestoneId;

    private Long pmsProjectId;

    private String milestoneCode;

    private String milestoneName;

    private String description;

    private Long pmsEpicId;

    private LocalDate plannedStartDate;

    private LocalDate plannedDueDate;

    private LocalDate actualCompletionDate;

    /** PMS execution status, passed through as text so a new PMS value never breaks the API. */
    private String executionStatus;

    private Boolean billable;

    /** Share of the contract value this milestone bills, in percent (0-100, two decimals). */
    private BigDecimal allocationPercentage;

    /** NOT_BILLABLE, NOT_READY or READY_FOR_BILLING (as synchronised from PMS). */
    private String billingReadiness;

    /** PMS optimistic-lock version of the synchronised row. */
    private Long sourceVersion;

    private LocalDateTime createdAt;

    /** When PMS last changed the milestone. */
    private LocalDateTime changedAt;

    /** When AR last applied a change from PMS (lets a screen show how fresh the data is). */
    private LocalDateTime lastSyncedAt;

    /** Where the milestone stands in AR billing (derived, see {@link MilestoneBillingState}). */
    private MilestoneBillingState billingState;

    /** The billing configuration whose plan holds the milestone; null when {@code billingState} is AVAILABLE. */
    private UUID billingConfigurationId;

    /**
     * Whether the milestone can be selected for a billing plan right now: AVAILABLE, billable and READY_FOR_BILLING in PMS.
     * The server applies the same rule when a plan is saved, so a screen can rely on it to enable or disable the selection.
     * A milestone that is not eligible yet becomes eligible by itself when PMS marks it ready; it needs no new plan.
     */
    private Boolean eligibleForBilling;

    /** Why {@code eligibleForBilling} is false; null when it is true. */
    private String ineligibilityReason;
}
