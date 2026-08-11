package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.PricingModel;
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
public class BillingConfigurationResponseDto {

    private UUID billingConfigurationId;

    private UUID clientId;

    private String clientName;

    private Long projectId;

    private String projectName;

    private BillingConfigurationStatus status;

    private UUID billingTypeId;

    private String billingTypeName;

    private Long projectcode;

//    private UUID currencyId;
//
//    private String currencyCode;
private String currency;

    private BigDecimal projectBudget;

    private String projectBudgetCurrency;

    private UUID paymentTermId;

    private String paymentTermName;

    private UUID billingFrequencyId;

    private String billingFrequencyName;

    private UUID taxRegionId;

    private PricingModel pricingModel;

    private InvoiceGenerationType invoiceGenerationType;

    private String taxRegionName;

    private String taxRegionCode;

    private Boolean expenseBillingEligible;

    private String rejectionReason;

    private Boolean isActive;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer versionNo;

    private String createdBy;

    private LocalDateTime createdDate;

    private Boolean active;

    private BigDecimal hourlyRate;

    private BigDecimal contractValue;

}
