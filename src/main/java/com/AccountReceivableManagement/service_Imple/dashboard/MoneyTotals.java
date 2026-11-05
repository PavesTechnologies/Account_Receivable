package com.AccountReceivableManagement.service_Imple.dashboard;

import com.AccountReceivableManagement.dto.dashboard.CurrencyAmountDto;
import com.AccountReceivableManagement.dto.dashboard.MoneyMetricDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Accumulates a count and per-currency sums. Currencies are never mixed;
 * a missing currency code is reported as {@value #UNKNOWN_CURRENCY}.
 */
final class MoneyTotals {

    static final String UNKNOWN_CURRENCY = "UNKNOWN";

    private final Map<String, BigDecimal> byCurrency = new TreeMap<>();
    private long count;

    /** Totals that carry only a count, with no amounts. */
    static MoneyTotals ofCount(long count) {
        MoneyTotals totals = new MoneyTotals();
        totals.count = Math.max(0, count);
        return totals;
    }

    /** Adds one item. */
    MoneyTotals add(String currencyCode, BigDecimal amount) {
        return add(currencyCode, amount, 1);
    }

    /** Adds a pre-aggregated group of {@code items} items. */
    MoneyTotals add(String currencyCode, BigDecimal amount, long items) {
        String code = currencyCode == null || currencyCode.isBlank() ? UNKNOWN_CURRENCY : currencyCode.trim().toUpperCase();
        byCurrency.merge(code, amount == null ? BigDecimal.ZERO : amount, BigDecimal::add);
        count += items;
        return this;
    }

    MoneyTotals addAll(MoneyTotals other) {
        other.byCurrency.forEach((code, amount) -> byCurrency.merge(code, amount, BigDecimal::add));
        count += other.count;
        return this;
    }

    long count() {
        return count;
    }

    List<CurrencyAmountDto> amounts() {
        return byCurrency.entrySet().stream()
                .map(e -> CurrencyAmountDto.builder()
                        .currencyCode(e.getKey())
                        .amount(e.getValue().setScale(2, RoundingMode.HALF_UP))
                        .build())
                .toList();
    }

    MoneyMetricDto toMetric() {
        return MoneyMetricDto.builder().count(count).amounts(amounts()).build();
    }
}
