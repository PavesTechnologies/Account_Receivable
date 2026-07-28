package com.AccountReceivableManagement.dto.projectbilling_config;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillingTypeResponseDto {

    private UUID billingTypeId;

    private String billingTypeName;

    private String description;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
