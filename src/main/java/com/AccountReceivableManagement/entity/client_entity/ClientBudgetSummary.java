package com.AccountReceivableManagement.entity.client_entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_budget_summary", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"client_id", "currency"})
})
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
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "total_budget", precision = 18, scale = 2)
    private BigDecimal totalBudget;

    @Column(name = "project_count")
    private Long projectCount;

    @Column(name = "last_calculated_at")
    private LocalDateTime lastCalculatedAt;
}
