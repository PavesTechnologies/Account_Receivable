package com.AccountReceivableManagement.Service_Imple.billing_data_acquisition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Result of calculating billing totals for a snapshot being created.
 * Internal to the Service orchestration layer — Stories 2.2-2.5 extend
 * this as expense/milestone/retainer calculations are added, without
 * changing the Builder or its context.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingAmountSummary {

    private BigDecimal subtotal;

    private BigDecimal expenseAmount;

    private BigDecimal totalAmount;
}
