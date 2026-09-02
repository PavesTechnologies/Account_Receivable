package com.AccountReceivableManagement.entity.tax_calculation;

import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxApplicabilityType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "tax_calculation_component",
        indexes = {
                @Index(
                        name = "idx_tax_calc_component_calculation",
                        columnList = "tax_calculation_id"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxCalculationComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tax_calculation_component_id")
    private UUID taxCalculationComponentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "tax_calculation_id",
            nullable = false
    )
    private TaxCalculation taxCalculation;

    @Column(
            name = "tax_type_id",
            nullable = false
    )
    private UUID taxTypeId;

    @Column(
            name = "tax_type_code",
            nullable = false,
            length = 50
    )
    private String taxTypeCode;

    @Column(
            name = "tax_type_name",
            nullable = false,
            length = 100
    )
    private String taxTypeName;

    @Column(
            name = "applied_rate",
            nullable = false,
            precision = 10,
            scale = 4
    )
    private BigDecimal appliedRate;

    @Column(
            name = "tax_amount",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal taxAmount;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "applicability_type",
            nullable = false,
            length = 30
    )
    private TaxApplicabilityType applicabilityType;
}
