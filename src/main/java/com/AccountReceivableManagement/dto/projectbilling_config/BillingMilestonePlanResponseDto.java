package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.PaymentStructure;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillingMilestonePlanResponseDto {

    private UUID milestonePlanId;

    private UUID billingConfigurationId;

    private BigDecimal totalContractValue;

    private PaymentStructure paymentStructure;

    private List<BillingPaymentEntryResponseDto> entries;

    private String remarks;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
