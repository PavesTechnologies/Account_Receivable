package com.AccountReceivableManagement.dto.projectbilling_config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyRequestDto {

    @NotBlank(message = "Currency code is required.")
    @Size(max = 10, message = "Currency code cannot exceed 10 characters.")
    private String currencyCode;

    @NotBlank(message = "Currency name is required.")
    @Size(max = 100, message = "Currency name cannot exceed 100 characters.")
    private String currencyName;

    @Size(max = 10, message = "Currency symbol cannot exceed 10 characters.")
    private String currencySymbol;

    @Size(max = 500, message = "Description cannot exceed 500 characters.")
    private String description;

}
