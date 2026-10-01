package com.AccountReceivableManagement.entity.projectbilling_config;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_payment_entry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingPaymentEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payment_entry_id")
    private UUID paymentEntryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "milestone_plan_id",
            referencedColumnName = "milestone_plan_id",
            nullable = false
    )
    private BillingMilestonePlan milestonePlan;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(
            name = "percentage",
            nullable = false,
            precision = 5,
            scale = 2
    )
    private BigDecimal percentage;

    @Column(
            name = "amount",
            nullable = false,
            precision = 18,
            scale = 2
    )
    private BigDecimal amount;

    @Column(name = "billing_date")
    private LocalDate billingDate;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
