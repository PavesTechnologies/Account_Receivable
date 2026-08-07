package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationUnit;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalPricingType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillingSubscriptionResponseDto {

    private UUID subscriptionConfigurationId;

    private String subscriptionName;

    private BigDecimal contractValue;

    private LocalDate subscriptionStartDate;

    private LocalDate subscriptionEndDate;

    private RenewalType renewalType;

    private RenewalDurationType renewalDurationType;

    private Integer renewalDurationValue;

    private RenewalDurationUnit renewalDurationUnit;

    private RenewalPricingType renewalPricingType;

    private BigDecimal renewalContractValue;

    private UUID renewalBillingFrequencyId;

    private String renewalBillingFrequencyName;

    private LocalDate renewalEffectiveFrom;

    private String remarks;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
