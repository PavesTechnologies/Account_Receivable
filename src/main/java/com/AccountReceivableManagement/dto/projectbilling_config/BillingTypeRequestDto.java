package com.AccountReceivableManagement.dto.projectbilling_config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingTypeRequestDto {
    @NotBlank(message = "Billing Type Name is required.")
    @Size(max = 100)
    private String billingTypeName;

    @Size(max = 500)
    private String description;
}
