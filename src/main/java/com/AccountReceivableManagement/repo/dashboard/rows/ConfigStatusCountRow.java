package com.AccountReceivableManagement.repo.dashboard.rows;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;

/** Billing configuration count per approval status and billing status. */
public record ConfigStatusCountRow(ApprovalStatus approvalStatus, BillingConfigurationStatus billingStatus, Long count) {
}
