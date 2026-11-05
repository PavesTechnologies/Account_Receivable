package com.AccountReceivableManagement.repo.dashboard.rows;

import java.math.BigDecimal;

/** Invoiced grand totals for one calendar month and currency. */
public record MonthTotalRow(Integer year, Integer month, String currencyCode, BigDecimal amount, Long count) {
}
