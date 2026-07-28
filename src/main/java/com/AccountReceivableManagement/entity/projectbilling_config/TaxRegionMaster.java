package com.AccountReceivableManagement.entity.projectbilling_config;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tax_region_master")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxRegionMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tax_region_id")
    private UUID taxRegionId;

    @Column(name = "tax_region_code", nullable = false, unique = true, length = 10)
    private String taxRegionCode;

    @Column(name = "tax_region_name", nullable = false, length = 100)
    private String taxRegionName;

    @Column(name = "tax_regime", nullable = false, length = 50)
    private String taxRegime;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode;

    @Column(name = "description", length = 500)
    private String description;

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
