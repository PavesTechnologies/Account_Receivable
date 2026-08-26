package com.AccountReceivableManagement.dto.projectbilling_config;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillingScheduleCalculationRequestDto {

    @NotNull(message = "Start date is required.")
    private LocalDate startDate;

    @NotNull(message = "End date is required.")
    private LocalDate endDate;

    @NotNull(message = "Duration value is required.")
    private Integer durationValue;

    @NotNull(message = "Duration unit is required.")
    private String durationUnit;

    @NotNull(message = "Total contract value is required.")
    private BigDecimal totalContractValue;
}
