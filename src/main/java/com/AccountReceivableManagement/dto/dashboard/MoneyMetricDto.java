package com.AccountReceivableManagement.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** A count with its per-currency totals. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoneyMetricDto {

    private long count;
    private List<CurrencyAmountDto> amounts;
}
