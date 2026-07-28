package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingConfigurationRequestDto {
    @NotNull(message = "Client is required.")
    private UUID clientId;

    @NotNull(message = "Project is required.")
    private Long projectId;

    @NotNull(message = "Effective From Date is required.")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private BillingType billingType;

    private String currencyCode;

    private String paymentTermCode;

    private String billingFrequency;

    private String taxRegionCode;

    private BigDecimal hourlyRate;

    private BigDecimal contractValue;

    private Boolean expenseBillingEligible;
}
