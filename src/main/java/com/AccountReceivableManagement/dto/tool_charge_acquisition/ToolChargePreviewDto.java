package com.AccountReceivableManagement.dto.tool_charge_acquisition;

import com.AccountReceivableManagement.entity_enums.tool_catalog.BillingBasis;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ToolChargePreviewDto {

    private UUID assignmentId;

    private Long projectId;

    private String projectName;

    private UUID toolId;

    private String assetCode;

    private String assetName;

    private BillingBasis billingBasis;

    private UUID currencyId;

    private String currencyCode;

    private String currencyName;

    private Integer quantity;

    private BigDecimal unitPrice;

    private BigDecimal calculatedAmount;

    private LocalDate billingPeriodStart;

    private LocalDate billingPeriodEnd;

    private LocalDate assignmentStartDate;

    private LocalDate assignmentEndDate;
}
