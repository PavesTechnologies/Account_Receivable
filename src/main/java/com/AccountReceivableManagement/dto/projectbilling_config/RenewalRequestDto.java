package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalOptionType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RenewalRequestDto {

    @NotNull(message = "Renewal Option Type is required.")
    private RenewalOptionType renewalOptionType;

    private BigDecimal recurringAmount;

    private UUID billingFrequencyId;

    @NotNull(message = "Effective From is required.")
    private LocalDate effectiveFrom;

    @NotNull(message = "Effective To is required.")
    private LocalDate effectiveTo;

    private String productName;

    private String productDescription;
}
