package com.AccountReceivableManagement.entity.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxApplicabilityType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "tax_configuration_component",
        indexes = {
                @Index(
                        name = "idx_tax_component_configuration",
                        columnList = "tax_configuration_id"
                ),
                @Index(
                        name = "idx_tax_component_type",
                        columnList = "tax_type_id"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxConfigurationComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tax_configuration_component_id")
    private UUID taxConfigurationComponentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "tax_configuration_id",
            nullable = false
    )
    private TaxConfiguration taxConfiguration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "tax_type_id",
            nullable = false
    )
    private TaxTypeMaster taxType;

    @Column(
            name = "tax_rate",
            nullable = false,
            precision = 10,
            scale = 4
    )
    private BigDecimal taxRate;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "applicability_type",
            nullable = false,
            length = 30
    )
    private TaxApplicabilityType applicabilityType;

    @Column(
            name = "is_active",
            nullable = false
    )
    @Builder.Default
    private Boolean isActive = true;
}
