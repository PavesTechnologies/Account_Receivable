package com.AccountReceivableManagement.dto.projectbilling_config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingFrequencyRequestDto {
    @NotBlank(message = "Billing Frequency Name is required.")
    @Size(max = 100)
    private String billingFrequencyName;

    @Size(max = 500)
    private String description;
}
