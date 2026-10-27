package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;

import java.util.UUID;

/**
 * One active payment entry that claims a PMS milestone, with the billing occurrence generated for it (all occurrence
 * fields null when none exists yet). Result row of {@link BillingPaymentEntryRepository#findMilestoneAssignmentRows}.
 */
public record MilestoneAssignmentRow(
        Long pmsMilestoneId,
        UUID billingConfigurationId,
        ApprovalStatus approvalStatus,
        BillingConfigurationStatus billingStatus,
        UUID billingScheduleId,
        BillingPeriodStatus occurrenceStatus,
        Boolean invoiced) {
}
