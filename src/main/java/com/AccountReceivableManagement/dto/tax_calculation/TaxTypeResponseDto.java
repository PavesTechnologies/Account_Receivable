package com.AccountReceivableManagement.dto.tax_calculation;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TaxTypeResponseDto {

    private UUID taxTypeId;

    private String taxTypeCode;

    private String taxTypeName;

    private String description;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
