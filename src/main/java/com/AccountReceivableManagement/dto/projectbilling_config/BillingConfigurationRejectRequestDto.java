package com.AccountReceivableManagement.dto.projectbilling_config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillingConfigurationRejectRequestDto {

    @NotBlank(message = "Rejection reason is required.")
    @Size(max = 500)
    private String rejectionReason;
}
