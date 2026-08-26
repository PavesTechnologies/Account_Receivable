package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.ContractValueSource;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationUnit;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalPricingType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RecurringBillingRequestDto {

    private String recurringName;

    private BigDecimal contractValue;

    @NotNull(message = "Contract Value Source is required.")
    private ContractValueSource contractValueSource;

    @NotNull(message = "Recurring Start Date is required.")
    private LocalDate recurringStartDate;

    @NotNull(message = "Recurring End Date is required.")
    private LocalDate recurringEndDate;

    @NotNull(message = "Billing Frequency is required for recurring billing.")
    private UUID billingFrequencyId;

    private RenewalType renewalType;

    // Required only when RenewalType = AUTO
    private RenewalDurationType renewalDurationType;

    private Integer renewalDurationValue;

    private RenewalDurationUnit renewalDurationUnit;

    private RenewalPricingType renewalPricingType;

    private BigDecimal renewalContractValue;

    private UUID renewalBillingFrequencyId;

    private LocalDate renewalEffectiveFrom;

    private String remarks;
}
