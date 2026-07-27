package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingConfigurationResponseDto {

    private UUID billingConfigurationId;

    private UUID clientId;

    private Long projectId;

    private BillingConfigurationStatus status;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private Integer versionNo;

    private String createdBy;

    private LocalDateTime createdDate;

    private Boolean active;

}
