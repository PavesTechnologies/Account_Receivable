package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.TaxComponentInputType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxComponentRequestDto {

    @NotNull(message = "Tax regime is required.")
    private UUID taxRegimeId;

    @NotNull(message = "Tax type is required.")
    private UUID taxTypeId;

    @NotBlank(message = "Component code is required.")
    @Size(max = 50, message = "Component code must not exceed 50 characters.")
    private String componentCode;

    @NotBlank(message = "Component name is required.")
    @Size(max = 100, message = "Component name must not exceed 100 characters.")
    private String componentName;

    @Size(max = 500, message = "Description must not exceed 500 characters.")
    private String description;

    private TaxComponentInputType inputType;

    @NotNull(message = "Display order is required.")
    @Min(value = 0, message = "Display order must be non-negative.")
    private Integer displayOrder;

    @NotNull(message = "Effective from date is required.")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;
}
