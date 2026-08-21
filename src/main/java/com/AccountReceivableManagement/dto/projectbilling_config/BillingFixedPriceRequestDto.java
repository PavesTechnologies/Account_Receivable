package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.ContractValueSource;
import jakarta.validation.constraints.DecimalMax;
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

    @NotNull(message = "Contract Value is required.")
    @DecimalMin(
            value = "0.01",
            message = "Contract Value must be greater than zero."
    )
    private BigDecimal contractValue;

    /**
     * PMS budget/reference value.
     */
    @DecimalMin(
            value = "0.00",
            message = "Project Budget cannot be negative."
    )
    private BigDecimal pmsProjectBudget;

    @NotNull(message = "Contract Value Source is required.")
    private ContractValueSource contractValueSource;

    @DecimalMin(
            value = "0.00",
            message = "Retention percentage cannot be negative."
    )
    @DecimalMax(
            value = "100.00",
            message = "Retention percentage cannot exceed 100."
    )
    private BigDecimal retentionPercentage;

    @DecimalMin(
            value = "0.00",
            message = "Advance Received cannot be negative."
    )
    private BigDecimal advanceReceived;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private String remarks;

}
