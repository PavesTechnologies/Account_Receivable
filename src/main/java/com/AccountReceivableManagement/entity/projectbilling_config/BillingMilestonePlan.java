package com.AccountReceivableManagement.entity.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.PaymentStructure;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_milestone_plan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingMilestonePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "milestone_plan_id")
    private UUID milestonePlanId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "billing_configuration_id",
            referencedColumnName = "billing_configuration_id",
            nullable = false,
            unique = true
    )
    private BillingConfiguration billingConfiguration;

    @Column(
            name = "total_contract_value",
            nullable = false,
            precision = 18,
            scale = 2
    )
    private BigDecimal totalContractValue;

    // JdbcTypeCode(VARCHAR): without it Hibernate's MySQL dialect creates a native ENUM frozen to the constants that existed
    // at table-creation time, which cannot store MILESTONES (see migration V16__milestone_billing_support.sql).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(
            name = "payment_structure",
            nullable = false,
            length = 30
    )
    private PaymentStructure paymentStructure;

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
