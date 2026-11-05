package com.AccountReceivableManagement.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** One calendar month of invoicing. collected stays null until payments are recorded. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrendPointDto {

    /** yyyy-MM */
    private String month;
    private long invoiceCount;
    private List<CurrencyAmountDto> invoiced;
    private List<CurrencyAmountDto> collected;
}
