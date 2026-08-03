package com.AccountReceivableManagement.dto.projectbilling_config;

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

    @NotNull(message = "Billing Type is required.")
    private UUID billingTypeId;

    @NotNull(message = "Currency is required.")
    private UUID currencyId;

    @NotNull(message = "Payment Term is required.")
    private UUID paymentTermId;

    @NotNull(message = "Billing Frequency is required.")
    private UUID billingFrequencyId;

    @NotNull(message = "Tax Region is required.")
    private UUID taxRegionId;

    @NotNull(message = "Expense Billing Eligibility is required.")
    private Boolean expenseBillingEligible;

    @NotNull(message = "Effective From Date is required.")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private BigDecimal hourlyRate;

    private BigDecimal contractValue;
}
