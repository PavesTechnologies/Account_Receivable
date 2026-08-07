package com.AccountReceivableManagement.dto.invoice_software_selection;

import com.AccountReceivableManagement.entity_enums.tool_catalog.BillingBasis;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What Finance sees inside Invoice Draft → Additional Charges →
 * Software / Tools / Licenses. Merges RMS's operational assignment data
 * (assetId..assignmentEndDate) with AR's Tool Pricing (unitPrice..description)
 * for the same assetId. Carries no computed amount - charge calculation is a
 * later phase.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceSoftwareSelectionResponseDto {

    private UUID assetId;

    private String assetCode;

    private String assetName;

    private String assetCategory;

    private Integer quantity;

    private BillingBasis billingBasis;

    private LocalDate assignmentStartDate;

    private LocalDate assignmentEndDate;

    private BigDecimal unitPrice;

    private UUID currencyId;

    private String currencyCode;

    private String currencyName;

    private String description;

    private Boolean selectionEligible;

    private String selectionReason;
}
