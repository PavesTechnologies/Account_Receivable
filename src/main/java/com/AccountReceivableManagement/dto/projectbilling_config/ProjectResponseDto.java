package com.AccountReceivableManagement.dto.projectbilling_config;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectResponseDto {

    private Long projectId;

    private String projectName;

    private String projectCode;

    private String projectDuration;

    private BigDecimal projectBudget;

    private String projectBudgetCurrency;

//    private Long projectManager;
}
