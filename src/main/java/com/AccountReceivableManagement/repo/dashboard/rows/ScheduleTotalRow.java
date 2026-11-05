package com.AccountReceivableManagement.repo.dashboard.rows;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Due, un-invoiced billing schedule totals per tax status and currency, with the earliest billing date. */
public record ScheduleTotalRow(
        BillingPeriodStatus taxStatus,
        String currencyCode,
        BigDecimal amount,
        Long count,
        LocalDate oldestBillingDate) {
}
