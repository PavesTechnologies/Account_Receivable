package com.AccountReceivableManagement.entity.projectbilling_config;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tax_rate_configuration",
        indexes = {
                @Index(name = "idx_tax_rate_configuration_region", columnList = "tax_region_id"),
                @Index(name = "idx_tax_rate_configuration_active", columnList = "is_active")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxRateConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tax_rate_configuration_id")
    private UUID taxRateConfigurationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "tax_region_id",
            referencedColumnName = "tax_region_id",
            nullable = false
    )
    private TaxRegionMaster taxRegion;

    @Column(name = "tax_type", nullable = false, length = 50)
    private String taxType;

    @Column(name = "cgst_rate", precision = 10, scale = 4)
    private BigDecimal cgstRate;

    @Column(name = "sgst_rate", precision = 10, scale = 4)
    private BigDecimal sgstRate;

    @Column(name = "igst_rate", precision = 10, scale = 4)
    private BigDecimal igstRate;

    @Column(name = "effective_from", nullable = false)
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
