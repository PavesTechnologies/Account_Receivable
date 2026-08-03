package com.AccountReceivableManagement.dto.projectbilling_config;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingTMRateCardResponseDto {

    private UUID rateCardId;

    private String roleName;

    private BigDecimal hourlyRate;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private String remarks;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
