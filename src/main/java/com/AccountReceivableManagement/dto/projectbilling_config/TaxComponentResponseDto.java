package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.TaxComponentInputType;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxComponentResponseDto {

    private UUID taxComponentId;

    private UUID taxRegimeId;

    private String taxRegimeCode;

    private String taxRegimeName;

    private UUID taxTypeId;

    private String taxTypeCode;

    private String taxTypeName;

    private String componentCode;

    private String componentName;

    private String description;

    private TaxComponentInputType inputType;

    private Integer displayOrder;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
