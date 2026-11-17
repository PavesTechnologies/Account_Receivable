package com.AccountReceivableManagement.dto.projectbilling_config;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillingPeriodDto {

    private Integer periodNumber;

    private LocalDate periodStartDate;

    private LocalDate periodEndDate;

    /**
     * Date the period becomes due for billing. Set by
     * BillingOccurrenceServiceImpl.resolveTimeAndMaterialBillingPeriods;
     * {@code null} from the schedule-preview calculations.
     */
    private LocalDate billingDate;

    private BigDecimal billingAmount;

    private Boolean isPartialPeriod;
}
