package com.AccountReceivableManagement.dto.projectbilling_config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillingTMRateCardRequestDto {
    @NotBlank(message = "Role Name is required.")
    private String roleName;

    @NotNull(message = "Hourly Rate is required.")
    @DecimalMin(value = "0.01")
    private BigDecimal hourlyRate;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private String remarks;
}
