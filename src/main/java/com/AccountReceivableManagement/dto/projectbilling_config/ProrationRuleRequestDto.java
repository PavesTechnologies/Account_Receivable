package com.AccountReceivableManagement.dto.projectbilling_config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProrationRuleRequestDto {

    @NotBlank(message = "Proration rule code is required.")
    @Size(max = 20, message = "Proration rule code cannot exceed 20 characters.")
    private String prorationRuleCode;

    @NotBlank(message = "Proration rule name is required.")
    @Size(max = 100, message = "Proration rule name cannot exceed 100 characters.")
    private String prorationRuleName;

    @Size(max = 500, message = "Description cannot exceed 500 characters.")
    private String description;

}
