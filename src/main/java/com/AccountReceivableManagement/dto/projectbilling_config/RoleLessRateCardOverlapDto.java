package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.PricingModel;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only diagnostic row: two active role-less (Standard) rate cards of one configuration whose inclusive
 * effective windows overlap. Standard billing fails on any work date both cards cover.
 */
public record RoleLessRateCardOverlapDto(
        UUID billingConfigurationId,
        PricingModel pricingModel,
        ApprovalStatus approvalStatus,
        BillingConfigurationStatus billingStatus,
        UUID rateCardIdA, LocalDate effectiveFromA, LocalDate effectiveToA,
        UUID rateCardIdB, LocalDate effectiveFromB, LocalDate effectiveToB) {
}
