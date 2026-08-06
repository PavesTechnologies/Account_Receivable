package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationUnit;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalPricingType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
public class BillingSubscriptionRequestDto {

    @NotBlank(message = "Subscription Name is required.")
    private String subscriptionName;

    @NotNull(message = "Contract Value is required.")
    @DecimalMin(value = "0.01")
    private BigDecimal contractValue;

    @NotNull(message = "Subscription Start Date is required.")
    private LocalDate subscriptionStartDate;

    @NotNull(message = "Subscription End Date is required.")
    private LocalDate subscriptionEndDate;

    @NotNull(message = "Renewal Type is required.")
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
