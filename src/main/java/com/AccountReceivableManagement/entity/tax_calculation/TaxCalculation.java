package com.AccountReceivableManagement.entity.tax_calculation;

import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxCalculationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
@Table(name = "tax_calculation")
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

    @Column(name = "billing_snapshot_id", nullable = false, unique = true)
    private UUID billingSnapshotId;

    @Column(name = "tax_region_id", nullable = false)
    private UUID taxRegionId;

    @Column(name = "tax_rate_configuration_id", nullable = false)
    private UUID taxRateConfigurationId;

    @Column(name = "taxable_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxableAmount;

    @Column(name = "cgst_rate", precision = 10, scale = 4)
    private BigDecimal cgstRate;

    @Column(name = "cgst_amount", precision = 19, scale = 2)
    private BigDecimal cgstAmount;

    @Column(name = "sgst_rate", precision = 10, scale = 4)
    private BigDecimal sgstRate;

    @Column(name = "sgst_amount", precision = 19, scale = 2)
    private BigDecimal sgstAmount;

    @Column(name = "igst_rate", precision = 10, scale = 4)
    private BigDecimal igstRate;

    @Column(name = "igst_amount", precision = 19, scale = 2)
    private BigDecimal igstAmount;

    @Column(name = "total_tax_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalTaxAmount;

    @Column(name = "grand_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal grandTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaxCalculationStatus status;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

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
