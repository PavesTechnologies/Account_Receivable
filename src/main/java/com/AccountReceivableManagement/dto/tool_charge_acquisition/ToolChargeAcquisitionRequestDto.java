package com.AccountReceivableManagement.dto.tool_charge_acquisition;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolChargeAcquisitionRequestDto {

    @NotNull(message = "Project is required.")
    private Long projectId;

    @NotNull(message = "Billing Period Start is required.")
    private LocalDate billingPeriodStart;

    @NotNull(message = "Billing Period End is required.")
    private LocalDate billingPeriodEnd;
}
