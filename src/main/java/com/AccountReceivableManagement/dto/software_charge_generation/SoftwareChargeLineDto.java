package com.AccountReceivableManagement.dto.software_charge_generation;

import com.AccountReceivableManagement.entity_enums.tool_catalog.BillingBasis;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One calculated, runtime-only invoice charge line for a selected software
 * asset. Nothing here is persisted - charge lines are computed fresh from
 * {@code InvoiceSoftwareSelectionResponseDto} on each call and never stored.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoftwareChargeLineDto {

    private UUID assetId;

    private String assetCode;

    private String assetName;

    private BillingBasis billingBasis;

    private Integer quantity;

    private BigDecimal unitPrice;

    private UUID currencyId;

    private String currencyCode;

    private String currencyName;

    private LocalDate assignmentStartDate;

    private LocalDate assignmentEndDate;

    private String description;

    private BigDecimal calculatedAmount;
}
