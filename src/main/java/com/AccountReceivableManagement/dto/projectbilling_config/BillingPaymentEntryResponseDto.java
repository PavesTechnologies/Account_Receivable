package com.AccountReceivableManagement.dto.projectbilling_config;

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
public class BillingPaymentEntryResponseDto {

    private UUID paymentEntryId;

    private UUID milestonePlanId;

    private Integer sequence;

    private BigDecimal percentage;

    private BigDecimal amount;

    private LocalDate billingDate;

    private String remarks;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
