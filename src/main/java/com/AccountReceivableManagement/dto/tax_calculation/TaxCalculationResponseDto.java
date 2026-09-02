package com.AccountReceivableManagement.dto.tax_calculation;

import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxCalculationStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

    private String taxRegionName;

    private String taxRegionCode;

    private UUID taxRegionId;

    private UUID taxConfigurationId;

    private BigDecimal taxableAmount;

    private List<TaxCalculationComponentResponseDto> components;

    private BigDecimal totalTaxAmount;

    private BigDecimal grandTotal;

    private TaxCalculationStatus status;

    private LocalDateTime calculatedAt;
}
