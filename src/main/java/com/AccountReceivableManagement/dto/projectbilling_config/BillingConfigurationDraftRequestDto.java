package com.AccountReceivableManagement.dto.projectbilling_config;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillingConfigurationDraftRequestDto {

    @NotNull(message = "Client is required.")
    private UUID clientId;

    @NotNull(message = "Project is required.")
    private Long projectId;

    @NotNull(message = "Billing Type is required.")
    private UUID billingTypeId;

    private UUID billingFrequencyId;

    private UUID currencyId;

}
