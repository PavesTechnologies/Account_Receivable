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
public class TaxRateConfigurationResponseDto {

    private UUID taxRateConfigurationId;

    private UUID taxRegionId;

    private String taxRegionCode;

    private String taxRegionName;

    private String taxRegime;

    private String taxType;

    private BigDecimal cgstRate;

    private BigDecimal sgstRate;

    private BigDecimal igstRate;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
