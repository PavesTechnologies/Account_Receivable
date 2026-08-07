package com.AccountReceivableManagement.dto.tool_catalog;

import com.AccountReceivableManagement.entity_enums.tool_catalog.BillingBasis;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ToolCatalogResponseDto {

    private UUID toolId;

    private UUID assetId;

    private String assetCode;

    private String assetName;

    private String description;

    private BillingBasis billingBasis;

    private BigDecimal unitPrice;

    private UUID currencyId;

    private String currencyCode;

    private String currencyName;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
