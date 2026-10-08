package com.AccountReceivableManagement.dto.projectbilling_config;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxRegionResponseDto {

    private UUID taxRegionId;

    private String taxRegionCode;

    private String taxRegionName;

    /**
     * Tax regime string field kept for backward compatibility only.
     * The generic design supports multiple tax regimes per region.
     * TaxConfiguration links a specific TaxRegion to a specific TaxRegime.
     */
    private String taxRegime;

    private String currencyCode;

    private String description;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
