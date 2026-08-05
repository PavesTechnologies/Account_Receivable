package com.AccountReceivableManagement.entity.client_entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_budget_summary")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientBudgetSummary {

    @Id
    @GeneratedValue
    @Column(name = "summary_id")
    private UUID summaryId;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false, unique = true)
    private Client client;

    @Column(name = "total_budget", precision = 18, scale = 2)
    private BigDecimal totalBudget;

    @Column(name = "currency")
    private String currency;

    @Column(name = "last_calculated_at")
    private LocalDateTime lastCalculatedAt;
}
