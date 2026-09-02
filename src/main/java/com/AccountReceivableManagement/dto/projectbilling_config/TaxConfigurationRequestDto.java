package com.AccountReceivableManagement.dto.projectbilling_config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxConfigurationRequestDto {

    @NotNull(message = "Tax region is required.")
    private UUID taxRegionId;

    @NotBlank(message = "Tax regime is required.")
    @Size(max = 50)
    private String taxRegime;

    @NotNull(message = "Effective From date is required.")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @NotEmpty(message = "At least one tax component is required.")
    @Valid
    private List<TaxConfigurationComponentRequestDto> components;
}
