package com.AccountReceivableManagement.dto.projectbilling_config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingPaymentEntryRequestDto {

    @NotNull(message = "Sequence is required.")
    @Min(value = 1, message = "Sequence must be greater than zero.")
    private Integer sequence;

    @NotNull(message = "Percentage is required.")
    @DecimalMin(
            value = "0.01",
            message = "Percentage must be greater than zero."
    )
    private BigDecimal percentage;

    private LocalDate billingDate;

    private String remarks;

}
