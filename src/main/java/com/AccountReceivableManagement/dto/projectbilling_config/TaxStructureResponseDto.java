package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.TaxComponentInputType;
import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * DTO for tax structure discovery API.
 * Returns the complete tax structure for a tax region,
 * including available regimes and their components.
 * This allows the frontend to dynamically render tax fields
 * without hardcoding country-specific logic.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxStructureResponseDto {

    private TaxRegionInfo taxRegion;

    private List<TaxRegimeStructure> taxRegimes;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TaxRegionInfo {
        private UUID taxRegionId;
        private String taxRegionCode;
        private String taxRegionName;
        private String currencyCode;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TaxRegimeStructure {
        private UUID taxRegimeId;
        private String taxRegimeCode;
        private String taxRegimeName;
        private String description;
        private List<TaxComponentInfo> components;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TaxComponentInfo {
        private UUID taxComponentId;
        private UUID taxTypeId;
        private String taxTypeCode;
        private String taxTypeName;
        private String componentCode;
        private String componentName;
        private String description;
        private TaxComponentInputType inputType;
        private Integer displayOrder;
    }
}
