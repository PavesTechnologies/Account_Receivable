package com.AccountReceivableManagement.dto.tax_calculation;

import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxCalculationStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxCalculationResponseDto {

    private UUID taxCalculationId;

    private UUID billingSnapshotId;

    private String snapshotNumber;

    private String projectName;

    private String clientName;

    private LocalDate billingPeriodStart;

    private LocalDate billingPeriodEnd;

    private String currencyCode;

    private BillingSnapshotStatus snapshotStatus;

    /**
     * Human-readable tax region — the primary user-facing value.
     * {@link #taxRegionId} is retained for traceability only, not for display.
     */
    private String taxRegionName;

    private String taxRegionCode;

    private UUID taxRegionId;

    private UUID taxRateConfigurationId;

    private BigDecimal taxableAmount;

    private BigDecimal cgstRate;

    private BigDecimal cgstAmount;

    private BigDecimal sgstRate;

    private BigDecimal sgstAmount;

    private BigDecimal igstRate;

    private BigDecimal igstAmount;

    private BigDecimal totalTaxAmount;

    private BigDecimal grandTotal;

    private TaxCalculationStatus status;

    private LocalDateTime calculatedAt;
}
