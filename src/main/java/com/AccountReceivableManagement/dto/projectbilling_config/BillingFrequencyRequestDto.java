package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

    @NotNull(message = "Duration Value is required.")
    @Positive(message = "Duration Value must be positive.")
    private Integer durationValue;

    @NotNull(message = "Duration Unit is required.")
    private RenewalDurationUnit durationUnit;
}
