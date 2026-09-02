package com.AccountReceivableManagement.dto.tax_calculation;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxTypeRequestDto {

    @NotBlank(message = "Tax type code is required")
    @Size(max = 50, message = "Tax type code must not exceed 50 characters")
    private String taxTypeCode;

    @NotBlank(message = "Tax type name is required")
    @Size(max = 100, message = "Tax type name must not exceed 100 characters")
    private String taxTypeName;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}
