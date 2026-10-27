package com.AccountReceivableManagement.entity.projectbilling_config;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_payment_entry", indexes = {
        @Index(name = "idx_bpe_pms_milestone", columnList = "pms_milestone_id")
})
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

    /**
     * PMS id of the milestone this entry bills (MILESTONES payment structure only; NULL for FULL_PAYMENT and INSTALLMENTS
     * entries and for entries created before milestone billing existed). A logical reference to
     * project_milestone_reference.pms_milestone_id - deliberately not a foreign key or a JPA relationship, because the
     * milestone row is maintained by CDC and may be tombstoned (source_deleted) but never removed.
     */
    @Column(name = "pms_milestone_id")
    private Long pmsMilestoneId;

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
