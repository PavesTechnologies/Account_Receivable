package com.AccountReceivableManagement.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** An amount in one currency. Dashboard totals are never converted across currencies. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrencyAmountDto {

    private String currencyCode;
    private BigDecimal amount;
}
