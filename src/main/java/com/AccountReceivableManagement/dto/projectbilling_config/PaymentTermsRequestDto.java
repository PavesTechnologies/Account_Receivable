package com.AccountReceivableManagement.dto.projectbilling_config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTermsRequestDto {
    @NotBlank(message = "Payment Term Name is required.")
    @Size(max = 100)
    private String paymentTermName;

    @NotNull(message = "Payment Days is required.")
    @Min(value = 0, message = "Payment Days cannot be negative.")
    private Integer paymentDays;

    @Size(max = 500)
    private String description;
}
