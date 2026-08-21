package com.AccountReceivableManagement.entity.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.ContractValueSource;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_fixed_price_configuration")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingFixedPriceConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "fixed_price_configuration_id")
    private UUID fixedPriceConfigurationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "billing_configuration_id",
            referencedColumnName = "billing_configuration_id",
            nullable = false
    )
    private BillingConfiguration billingConfiguration;

    @Column(
            name = "contract_value",
            nullable = false,
            precision = 18,
            scale = 2
    )
    private BigDecimal contractValue;

    /**
     * Project budget received from PMS.
     * This is a reference value and does not automatically
     * override the Contract Value.
     */
    @Column(
            name = "pms_project_budget",
            precision = 18,
            scale = 2
    )
    private BigDecimal pmsProjectBudget;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "contract_value_source",
            nullable = false,
            length = 30
    )
    private ContractValueSource contractValueSource;

    @Column(
            name = "retention_percentage",
            precision = 5,
            scale = 2
    )
    private BigDecimal retentionPercentage;

    @Column(
            name = "advance_received",
            precision = 18,
            scale = 2
    )
    private BigDecimal advanceReceived;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "remarks", length = 500)
    private String remarks;

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

        if (retentionPercentage == null) {
            retentionPercentage = BigDecimal.ZERO;
        }

        if (advanceReceived == null) {
            advanceReceived = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
