package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.PricingModel;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillingConfigurationDraftRequestDto {

    @NotNull(message = "Client is required.")
    private UUID clientId;

    @NotNull(message = "Project is required.")
    private Long projectId;

    @NotNull(message = "Billing Type is required.")
    private UUID billingTypeId;

    private UUID billingFrequencyId;

    private UUID currencyId;

    private String currency;

    private UUID paymentTermId;

    private UUID taxRegionId;

    private PricingModel pricingModel;

    private InvoiceGenerationType invoiceGenerationType;

    private Boolean expenseBillingEligible;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private BigDecimal hourlyRate;

}
