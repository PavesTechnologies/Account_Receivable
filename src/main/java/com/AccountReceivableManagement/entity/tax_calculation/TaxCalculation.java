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
            nullable = false,
            unique = true
    )
    private UUID billingSnapshotId;

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
