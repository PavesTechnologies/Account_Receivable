package com.AccountReceivableManagement.entity.invoice_generation;

import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxApplicabilityType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One frozen invoice tax line, copied as-is from the persisted
 * {@code TaxCalculationComponent} it originates from. No tax type
 * (CGST/SGST/IGST) is hardcoded here — the component's type/rate/amount are
 * carried over verbatim from the already-computed TaxCalculation.
 */
@Entity
@Table(
        name = "invoice_tax_component",
        indexes = {
                @Index(
                        name = "idx_invoice_tax_component_invoice",
                        columnList = "invoice_id"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceTaxComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "invoice_tax_component_id")
    private UUID invoiceTaxComponentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "invoice_id",
            nullable = false
    )
    private Invoice invoice;

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
