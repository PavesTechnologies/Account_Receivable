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

    /**
     * Tax regime ID for dynamic regime reference.
     * If provided, takes precedence over taxRegime string.
     */
    private UUID taxRegimeId;

    /**
     * Tax regime string field kept for backward compatibility.
     * New implementations should use taxRegimeId.
     */
    @Size(max = 50)
    private String taxRegime;

    /**
     * A tax configuration needs its regime: by id (preferred) or, for
     * backward compatibility, by its string. Neither - e.g. a blank string -
     * would save a configuration without a regime.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @AssertTrue(message = "Tax regime is required.")
    public boolean isTaxRegimeProvided() {
        return taxRegimeId != null || (taxRegime != null && !taxRegime.isBlank());
    }

    @NotNull(message = "Effective From date is required.")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @NotEmpty(message = "At least one tax component is required.")
    @Valid
    private List<TaxConfigurationComponentRequestDto> components;
}
