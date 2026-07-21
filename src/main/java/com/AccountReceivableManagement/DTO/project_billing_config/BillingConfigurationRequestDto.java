package com.AccountReceivableManagement.DTO;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public class BillingConfigurationRequestDto {
    @NotNull(message = "Client is required.")
    private UUID clientId;

    @NotNull(message = "Project is required.")
    private UUID projectId;

    @NotNull(message = "Effective From Date is required.")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;
}
