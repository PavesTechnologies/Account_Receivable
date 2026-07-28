package com.AccountReceivableManagement.dto.projectbilling_config;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CurrencyResponseDto {

    private UUID currencyId;

    private String currencyCode;

    private String currencyName;

    private String currencySymbol;

    private String description;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
