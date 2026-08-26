package com.AccountReceivableManagement.dto.projectbilling_config;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillingScheduleCalculationResponseDto {

    private List<BillingPeriodDto> periods;

    private Integer totalPeriods;

    private BigDecimal totalScheduledAmount;
}
