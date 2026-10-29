package com.AccountReceivableManagement.repo.dashboard.rows;

import java.math.BigDecimal;

/** Sum and count of one money column for a single currency. */
public record CurrencyTotalRow(String currencyCode, BigDecimal amount, Long count) {
}
