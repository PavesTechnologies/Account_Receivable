package com.AccountReceivableManagement.entity.projectbilling_config;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "tax_configuration",
        indexes = {
                @Index(
                        name = "idx_tax_configuration_region",
                        columnList = "tax_region_id"
                ),
                @Index(
                        name = "idx_tax_configuration_active",
                        columnList = "is_active"
                ),
                @Index(
                        name = "idx_tax_configuration_effective",
                        columnList = "effective_from"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tax_configuration_id")
    private UUID taxConfigurationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "tax_region_id",
            nullable = false
    )
    private TaxRegionMaster taxRegion;

    @Column(
            name = "tax_regime",
            nullable = false,
            length = 50
    )
    private String taxRegime;

    @Column(
            name = "effective_from",
            nullable = false
    )
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(
            name = "is_active",
            nullable = false
    )
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(
            mappedBy = "taxConfiguration",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<TaxConfigurationComponent> components =
            new ArrayList<>();

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
