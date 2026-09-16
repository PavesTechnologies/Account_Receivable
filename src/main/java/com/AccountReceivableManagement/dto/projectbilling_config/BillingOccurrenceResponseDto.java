package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.dto.tax_calculation.TaxCalculationComponentResponseDto;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingScheduleType;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxCalculationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingOccurrenceResponseDto {

    private UUID billingScheduleId;
    private UUID billingConfigurationId;
    private UUID recurringConfigurationId;
    private Integer periodNumber;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    private LocalDate billingDate;
    private BigDecimal billingAmount;
    private BillingScheduleType scheduleType;
    private Boolean isPartialPeriod;
    private BillingPeriodStatus periodStatus;
    private BillingPeriodStatus taxStatus;
    private Boolean isInvoiced;
    private LocalDate invoiceDate;
    private String remarks;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Tax calculation details
    private UUID taxCalculationId;
    private TaxCalculationStatus taxCalculationStatus;
    private BigDecimal taxableAmount;
    private BigDecimal totalTaxAmount;
    private BigDecimal grandTotal;
    private LocalDateTime taxCalculatedAt;

    // Tax components
    private List<TaxCalculationComponentResponseDto> taxComponents;

    // Configuration details
    private String projectName;
    private String clientName;
    private String currencyCode;
    private String taxRegionName;
    private String taxRegionCode;
}
