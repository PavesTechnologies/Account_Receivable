package com.AccountReceivableManagement.dto.projectbilling_config;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingFrequencyResponseDto {
    private UUID billingFrequencyId;

    private String billingFrequencyName;

    private String description;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
