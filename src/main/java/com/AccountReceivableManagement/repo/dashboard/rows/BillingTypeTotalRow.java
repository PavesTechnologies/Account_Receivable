package com.AccountReceivableManagement.repo.dashboard.rows;

import java.math.BigDecimal;

/** Invoiced grand totals per billing type (null when it could not be resolved) and currency. */
public record BillingTypeTotalRow(String billingType, String currencyCode, BigDecimal amount, Long count) {
}
