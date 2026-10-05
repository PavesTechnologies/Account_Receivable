package com.AccountReceivableManagement.dto.projectbilling_config;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingConfigurationChangeDto {

    private String field;
    private String previousValue;
    private String newValue;
    private String category;
}
