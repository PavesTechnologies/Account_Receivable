package com.AccountReceivableManagement.dto.projectbilling_config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxRateConfigurationRequestDto {

    @NotNull(message = "Tax region is required.")
    private UUID taxRegionId;

    @NotBlank(message = "Tax type is required.")
    @Size(max = 50)
    private String taxType;

    @DecimalMin(value = "0.00", message = "Tax rates cannot be negative.")
    private BigDecimal cgstRate;

    @DecimalMin(value = "0.00", message = "Tax rates cannot be negative.")
    private BigDecimal sgstRate;

    @DecimalMin(value = "0.00", message = "Tax rates cannot be negative.")
    private BigDecimal igstRate;

    @NotNull(message = "Effective From date is required.")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;
}
