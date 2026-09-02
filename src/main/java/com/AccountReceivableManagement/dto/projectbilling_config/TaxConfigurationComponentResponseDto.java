package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxApplicabilityType;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxConfigurationComponentResponseDto {

    private UUID taxConfigurationComponentId;

    private UUID taxTypeId;

    private String taxTypeCode;

    private String taxTypeName;

    private BigDecimal taxRate;

    private TaxApplicabilityType applicabilityType;

    private Boolean isActive;
}
