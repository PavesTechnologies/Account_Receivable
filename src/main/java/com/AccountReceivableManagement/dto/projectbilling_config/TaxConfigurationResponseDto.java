package com.AccountReceivableManagement.dto.projectbilling_config;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxConfigurationResponseDto {

    private UUID taxConfigurationId;

    private UUID taxRegionId;

    private String taxRegionCode;

    private String taxRegionName;

    private String taxRegime;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private Boolean isActive;

    private List<TaxConfigurationComponentResponseDto> components;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
