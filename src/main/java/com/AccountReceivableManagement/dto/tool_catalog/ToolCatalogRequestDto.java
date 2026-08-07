package com.AccountReceivableManagement.dto.tool_catalog;

import com.AccountReceivableManagement.entity_enums.tool_catalog.BillingBasis;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * assetCode/assetName are deliberately NOT accepted here - RMS owns that
 * data. The service resolves them itself via {@code AssetLookupService},
 * keyed only by the {@code assetId} the caller supplies.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCatalogRequestDto {

    @NotNull(message = "Asset is required.")
    private UUID assetId;

    @Size(max = 500, message = "Description cannot exceed 500 characters.")
    private String description;

    @NotNull(message = "Billing basis is required.")
    private BillingBasis billingBasis;

    @NotNull(message = "Unit price is required.")
    @DecimalMin(value = "0.0", inclusive = true, message = "Unit price cannot be negative.")
    private BigDecimal unitPrice;

    @NotNull(message = "Currency is required.")
    private UUID currencyId;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private Boolean active;
}
