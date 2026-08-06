package com.AccountReceivableManagement.dto.projectbilling_config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingFixedPriceRequestDto {

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal contractValue;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private String remarks;
}
