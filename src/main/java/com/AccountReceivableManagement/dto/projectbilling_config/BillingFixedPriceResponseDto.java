package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.ContractValueSource;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillingFixedPriceResponseDto {

    private UUID fixedPriceConfigurationId;

    private UUID billingConfigurationId;

    private BigDecimal contractValue;

    private BigDecimal pmsProjectBudget;

    private ContractValueSource contractValueSource;

    private BigDecimal retentionPercentage;

    private BigDecimal retentionAmount;

    private BigDecimal billableAmount;

    private BigDecimal advanceReceived;

    private BigDecimal remainingReceivable;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private String remarks;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
