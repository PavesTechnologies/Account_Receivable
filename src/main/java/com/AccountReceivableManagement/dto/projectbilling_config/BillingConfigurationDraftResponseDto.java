package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import lombok.*;

import java.util.UUID;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingConfigurationDraftResponseDto {

    private UUID billingConfigurationId;

    private UUID clientId;

    private Long projectId;

    private UUID billingTypeId;

    private BillingConfigurationStatus status;

    private Boolean isActive;
}
