package com.AccountReceivableManagement.entity.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.TaxComponentInputType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a tax component within a tax regime.
 * Examples: CGST, SGST, IGST (under GST regime), VAT (under VAT regime)
 *
 * This entity allows tax components to be configured dynamically
 * per tax regime without hardcoding in Java code.
 */
@Entity
@Table(
        name = "tax_component_master",
        indexes = {
                @Index(
                        name = "idx_tax_component_regime",
                        columnList = "tax_regime_id"
                ),
                @Index(
                        name = "idx_tax_component_type",
                        columnList = "tax_type_id"
                ),
                @Index(
                        name = "idx_tax_component_active",
                        columnList = "is_active"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxComponentMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tax_component_id")
    private UUID taxComponentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "tax_regime_id",
            nullable = false
    )
    private TaxRegimeMaster taxRegime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "tax_type_id",
            nullable = false
    )
    private TaxTypeMaster taxType;

    @Column(
            name = "component_code",
            nullable = false,
            length = 50
    )
    private String componentCode;

    @Column(
            name = "component_name",
            nullable = false,
            length = 100
    )
    private String componentName;

    @Column(
            name = "description",
            length = 500
    )
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "input_type",
            nullable = false,
            length = 30
    )
    @Builder.Default
    private TaxComponentInputType inputType = TaxComponentInputType.PERCENTAGE;

    @Column(
            name = "display_order",
            nullable = false
    )
    @Builder.Default
    private Integer displayOrder = 0;

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

        if (displayOrder == null) {
            displayOrder = 0;
        }

        if (inputType == null) {
            inputType = TaxComponentInputType.PERCENTAGE;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
