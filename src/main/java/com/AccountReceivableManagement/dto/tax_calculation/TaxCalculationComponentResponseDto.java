package com.AccountReceivableManagement.dto.tax_calculation;

import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxApplicabilityType;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxCalculationComponentResponseDto {

    private UUID taxCalculationComponentId;

    private UUID taxTypeId;

    private String taxTypeCode;

    private String taxTypeName;

    private BigDecimal appliedRate;

    private BigDecimal taxAmount;

    private TaxApplicabilityType applicabilityType;
}
