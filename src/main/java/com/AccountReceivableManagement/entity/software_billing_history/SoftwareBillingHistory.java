package com.AccountReceivableManagement.entity.software_billing_history;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Epic 4 (Revised Architecture) - Phase 6. Audit record proving a given RMS
 * asset was already billed for a given billing period, keyed by
 * {@code assetId} + {@code billingPeriodStart} + {@code billingPeriodEnd} -
 * the new architecture's duplicate-billing boundary, replacing the obsolete
 * Project Tool Assignment based tracking. Deliberately carries no foreign key
 * to RMS (assetId is a plain, unvalidated reference) and no foreign key to
 * {@code BillingSnapshot} (billingSnapshotId is likewise a plain reference) -
 * this table only records history, it does not own or cascade with either.
 */
@Entity
@Table(
        name = "software_billing_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_software_billing_history_asset_period",
                columnNames = {"asset_id", "billing_period_start", "billing_period_end"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoftwareBillingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "history_id")
    private UUID historyId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "billing_snapshot_id", nullable = false)
    private UUID billingSnapshotId;

    @Column(name = "invoice_number", length = 30)
    private String invoiceNumber;

    @Column(name = "billing_period_start", nullable = false)
    private LocalDate billingPeriodStart;

    @Column(name = "billing_period_end", nullable = false)
    private LocalDate billingPeriodEnd;

    @Column(name = "quantity", precision = 19, scale = 2)
    private BigDecimal quantity;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", length = 10)
    private String currencyCode;

    @Column(name = "billed_at", nullable = false)
    private LocalDateTime billedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();

        if (billedAt == null) {
            billedAt = createdAt;
        }
    }
}
