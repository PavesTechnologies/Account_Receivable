package com.AccountReceivableManagement.dto.projectbilling_config;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingConfigurationRequestDto {
    @NotNull(message = "Client is required.")
    private UUID clientId;

    @NotNull(message = "Project is required.")
    private Long projectId;

    @NotNull(message = "Effective From Date is required.")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;
}
