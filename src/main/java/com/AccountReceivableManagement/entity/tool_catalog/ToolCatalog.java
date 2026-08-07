package com.AccountReceivableManagement.entity.tool_catalog;

import com.AccountReceivableManagement.entity.projectbilling_config.CurrencyMaster;
import com.AccountReceivableManagement.entity_enums.tool_catalog.BillingBasis;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Epic 4 - Tool / Software / License Billing (Phase 2, refactored to Tool
 * Pricing Configuration). RMS owns all Software/Tool/License asset master
 * data - this table no longer authors assets, it only stores AR's commercial
 * pricing (billing basis, currency, unit price, effective dates) for an asset
 * identified by {@code assetId}. {@code assetCode}/{@code assetName} are
 * display-only snapshots supplied by the caller, not editable business keys.
 * No project association here - assignment to projects is handled by Phase 3.
 */
@Entity
@Table(name = "tool_catalog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tool_id")
    private UUID toolId;

    @Column(name = "asset_id", nullable = false, unique = true)
    private UUID assetId;

    @Column(name = "asset_code", nullable = false, length = 50)
    private String assetCode;

    @Column(name = "asset_name", nullable = false, length = 200)
    private String assetName;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_basis", nullable = false, length = 20)
    private BillingBasis billingBasis;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", referencedColumnName = "currency_id", nullable = false)
    private CurrencyMaster currency;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
