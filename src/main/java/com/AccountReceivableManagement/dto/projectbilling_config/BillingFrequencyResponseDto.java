package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationUnit;
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

    private Integer durationValue;

    private RenewalDurationUnit durationUnit;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
