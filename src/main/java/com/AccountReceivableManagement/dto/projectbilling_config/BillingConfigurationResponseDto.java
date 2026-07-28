package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
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

    private Long projectId;

    private BillingConfigurationStatus status;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private Integer versionNo;

    private String createdBy;

    private LocalDateTime createdDate;

    private Boolean active;

    private BillingType billingType;

    private String currencyCode;

    private String paymentTermCode;

    private String billingFrequency;

    private String taxRegionCode;

    private BigDecimal hourlyRate;

    private BigDecimal contractValue;

    private Boolean expenseBillingEligible;

}
