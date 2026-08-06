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
public class BillingFixedPriceResponseDto {

    private UUID fixedPriceConfigurationId;

    private BigDecimal contractValue;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private String remarks;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
