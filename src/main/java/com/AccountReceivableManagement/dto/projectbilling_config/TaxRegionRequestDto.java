package com.AccountReceivableManagement.dto.projectbilling_config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SecondaryRow;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxRegionRequestDto {

    @NotBlank(message = "Tax Region Code is required.")
    @Size(max = 20)
    private String taxRegionCode;

    @NotBlank(message = "Tax Region Name is required.")
    @Size(max = 100)
    private String taxRegionName;

    @NotBlank(message = "Tax Regime is required.")
    @Size(max = 50)
    private String taxRegime;

    @NotBlank(message = "Currency Code is required.")
    @Size(max = 10)
    private String currencyCode;

    @Size(max = 500)
    private String description;

}
