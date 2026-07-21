package com.AccountReceivableManagement.DTO;

import com.AccountReceivableManagement.Entity_Enums.BillingConfigurationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class BillingConfigurationResponseDto {

    private UUID billingConfigurationId;

    private UUID clientId;

    private UUID projectId;

    private BillingConfigurationStatus status;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private Integer versionNo;

    private String createdBy;

    private LocalDateTime createdDate;
}
