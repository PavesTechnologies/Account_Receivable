package com.AccountReceivableManagement.dto.client;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientBudgetSummaryResponseDto {

    private UUID clientId;

    private String clientName;

    private List<CurrencyBudget> budgets;

    private LocalDateTime lastCalculatedAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrencyBudget {
        private String currency;
        private BigDecimal totalProjectBudget;
        private Long projectCount;
    }
}
