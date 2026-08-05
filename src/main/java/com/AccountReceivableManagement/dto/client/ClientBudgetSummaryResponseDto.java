package com.AccountReceivableManagement.dto.client;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientBudgetSummaryResponseDto {

    private UUID clientId;

    private String clientName;

    private BigDecimal totalBudget;

    private String currency;

    private Long totalProjects;

    private LocalDateTime lastCalculatedAt;
}
