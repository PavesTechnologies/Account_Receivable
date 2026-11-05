package com.AccountReceivableManagement.entity.tax_calculation;

import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxCalculationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The post-tax result computed for exactly one {@code BillingSnapshot}.
 * Holds a frozen (copied, not re-derived) view of the taxable amount, the
 * rate(s) actually applied, and the resulting tax/grand-total figures, so the
 * result stays historically accurate even if the source snapshot or the
 * underlying TaxRateConfiguration changes afterwards. References its parent
 * and the resolved rate configuration by plain UUID columns rather than JPA
 * relationships, matching the point-in-time-copy convention already used by
 * {@code BillingSnapshot} itself.
 */
@Entity
@Table(
        name = "tax_calculation",
        indexes = {
                @Index(
                        name = "idx_tax_calculation_snapshot",
                        columnList = "billing_snapshot_id"
                ),
                @Index(
                        name = "idx_tax_calculation_region",
                        columnList = "tax_region_id"
                ),
                @Index(
                        name = "idx_tax_calculation_configuration",
                        columnList = "tax_configuration_id"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class TaxCalculation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tax_calculation_id")
    private UUID taxCalculationId;

    @Column(
            name = "billing_snapshot_id",
            unique = true
    )
    private UUID billingSnapshotId;

    @Column(
            name = "billing_schedule_id",
            unique = true
    )
    private UUID billingScheduleId;

    @Column(
            name = "tax_region_id",
            nullable = false
    )
    private UUID taxRegionId;

    @Column(
            name = "tax_configuration_id",
            nullable = false
    )
    private UUID taxConfigurationId;

    /**
     * Currency the taxable amount was expressed in when tax was calculated
     * (G-12). Invoice generation uses it and refuses to invoice in another
     * currency. Nullable: calculations made before V12 did not record it.
     */
    @Column(name = "currency_code", length = 10)
    private String currencyCode;

    @Column(
            name = "taxable_amount",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal taxableAmount;

    @Column(
            name = "total_tax_amount",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal totalTaxAmount;

    @Column(
            name = "grand_total",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal grandTotal;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 20
    )
    private TaxCalculationStatus status;

    @Column(
            name = "calculated_at",
            nullable = false
    )
    private LocalDateTime calculatedAt;

    @OneToMany(
            mappedBy = "taxCalculation",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<TaxCalculationComponent> components =
            new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Set when an authorized recalculation replaced this calculation because
     * the occurrence's currency or tax region changed before it was invoiced
     * (BD-07 recovery). The row is kept as history: its billing_schedule_id
     * moves to {@code supersededBillingScheduleId}, so the occurrence can get
     * a new calculation without losing the old figures.
     */
    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    @Column(name = "superseded_billing_schedule_id")
    private UUID supersededBillingScheduleId;

    @Column(name = "superseded_by", length = 100)
    private String supersededBy;

    @Column(name = "superseded_by_user_id")
    private Long supersededByUserId;

    @Column(name = "superseded_reason", length = 500)
    private String supersededReason;

    @PrePersist
    public void prePersist() {

        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
