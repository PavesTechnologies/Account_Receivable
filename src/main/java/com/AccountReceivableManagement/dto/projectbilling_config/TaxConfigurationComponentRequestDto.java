package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxApplicabilityType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxConfigurationComponentRequestDto {

    @NotNull(message = "Tax type is required.")
    private UUID taxTypeId;

    @NotNull(message = "Tax rate is required.")
    @DecimalMin(
            value = "0.00",
            message = "Tax rate cannot be negative."
    )
    private BigDecimal taxRate;

    @NotNull(message = "Tax applicability is required.")
    private TaxApplicabilityType applicabilityType;
}
