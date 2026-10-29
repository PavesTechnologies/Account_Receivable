package com.AccountReceivableManagement.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Invoiced amount in the period for one billing type. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingModelSliceDto {

    private String billingType;
    private long count;
    private List<CurrencyAmountDto> amounts;
}
