package com.AccountReceivableManagement.entity.projectbilling_config;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a high-level tax regime for a country or jurisdiction.
 * Examples: GST (India), VAT (UK/SG), SALES_TAX (USA)
 *
 * This entity allows tax regimes to be configured dynamically
 * without hardcoding country-specific logic in Java code.
 */
@Entity
@Table(
        name = "tax_regime_master",
        indexes = {
                @Index(
                        name = "idx_tax_regime_code",
                        columnList = "tax_regime_code"
                ),
                @Index(
                        name = "idx_tax_regime_active",
                        columnList = "is_active"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxRegimeMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tax_regime_id")
    private UUID taxRegimeId;

    @Column(
            name = "tax_regime_code",
            nullable = false,
            unique = true,
            length = 50
    )
    private String taxRegimeCode;

    @Column(
            name = "tax_regime_name",
            nullable = false,
            length = 100
    )
    private String taxRegimeName;

    @Column(
            name = "description",
            length = 500
    )
    private String description;

    @Column(
            name = "is_active",
            nullable = false
    )
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(
            mappedBy = "taxRegime",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<TaxComponentMaster> components = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
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
