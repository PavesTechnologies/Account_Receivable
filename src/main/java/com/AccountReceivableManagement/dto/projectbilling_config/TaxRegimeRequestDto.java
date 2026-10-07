package com.AccountReceivableManagement.dto.projectbilling_config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxRegimeRequestDto {

    @NotBlank(message = "Tax regime code is required.")
    @Size(max = 50, message = "Tax regime code must not exceed 50 characters.")
    private String taxRegimeCode;

    @NotBlank(message = "Tax regime name is required.")
    @Size(max = 100, message = "Tax regime name must not exceed 100 characters.")
    private String taxRegimeName;

    @Size(max = 500, message = "Description must not exceed 500 characters.")
    private String description;
}
