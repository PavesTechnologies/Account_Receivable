package com.AccountReceivableManagement.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** Cash-collection metrics. AR records no payments or receipts yet, so tracked is false and every value is null;
 * the shape is fixed so a client can switch over once payments exist. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionsDto {

    private boolean tracked;
    private String reason;
    private List<CurrencyAmountDto> collected;
    private BigDecimal collectionRate;
    private BigDecimal dsoDays;
}
