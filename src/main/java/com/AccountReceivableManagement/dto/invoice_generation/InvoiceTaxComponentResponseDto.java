package com.AccountReceivableManagement.dto.invoice_generation;

import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxApplicabilityType;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceTaxComponentResponseDto {

    private UUID invoiceTaxComponentId;

    private UUID taxTypeId;

    private String taxTypeCode;

    private String taxTypeName;

    private BigDecimal appliedRate;

    private BigDecimal taxAmount;

    private TaxApplicabilityType applicabilityType;
}
