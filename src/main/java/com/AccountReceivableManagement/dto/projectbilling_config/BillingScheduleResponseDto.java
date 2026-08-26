package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingScheduleType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillingScheduleResponseDto {

    private UUID billingScheduleId;

    private UUID billingConfigurationId;

    private UUID subscriptionConfigurationId;

    private Integer periodNumber;

    private LocalDate periodStartDate;

    private LocalDate periodEndDate;

    private BigDecimal billingAmount;

    private BillingScheduleType scheduleType;

    private Boolean isPartialPeriod;

    private BillingPeriodStatus periodStatus;

    private Boolean isInvoiced;

    private LocalDate invoiceDate;

    private String remarks;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
