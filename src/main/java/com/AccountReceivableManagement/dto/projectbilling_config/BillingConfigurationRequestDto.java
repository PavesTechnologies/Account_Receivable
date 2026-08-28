package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.PricingModel;
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

//    @NotNull(message = "Currency is required.")
//    private UUID currencyId;

    @NotNull(message = "Currency is required.")
    private String currency;

    @NotNull(message = "Payment Term is required.")
    private UUID paymentTermId;

    @NotNull(message = "Billing Frequency is required.")
    private UUID billingFrequencyId;

    @NotNull(message = "Tax Region is required.")
    private UUID taxRegionId;

    private PricingModel pricingModel;

    @NotNull(message = "Invoice Generation Type is required.")
    private InvoiceGenerationType invoiceGenerationType;

    private BigDecimal contractValue;

    @NotNull(message = "Expense Billing Eligibility is required.")
    private Boolean expenseBillingEligible;

    @NotNull(message = "Effective From Date is required.")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private BigDecimal hourlyRate;

    /**
     * When true, this represents the final save operation from the UI.
     * It does not activate the billing configuration.
     * Activation is determined only after approval and based on
     * effectiveFrom/effectiveTo.
     */
    private Boolean finalize;

}
