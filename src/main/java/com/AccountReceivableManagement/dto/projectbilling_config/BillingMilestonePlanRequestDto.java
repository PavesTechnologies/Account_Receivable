package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.PaymentStructure;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingMilestonePlanRequestDto {

    @NotNull(message = "Total Contract Value is required.")
    @DecimalMin(
            value = "0.01",
            message = "Total Contract Value must be greater than zero."
    )
    private BigDecimal totalContractValue;

    @NotNull(message = "Payment Structure is required.")
    private PaymentStructure paymentStructure;

    @Valid
    private List<BillingPaymentEntryRequestDto> entries;

    private String remarks;

}
