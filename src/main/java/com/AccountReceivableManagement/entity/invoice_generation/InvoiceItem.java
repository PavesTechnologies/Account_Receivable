package com.AccountReceivableManagement.entity.invoice_generation;

import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One frozen invoice line, copied as-is from the {@code BillingSnapshotItem}
 * it originates from — no recalculation, no re-query of TMS or any other
 * source system.
 */
@Entity
@Table(
        name = "invoice_item",
        indexes = {
                @Index(
                        name = "idx_invoice_item_invoice",
                        columnList = "invoice_id"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "invoice_item_id")
    private UUID invoiceItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "invoice_id",
            nullable = false
    )
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "item_type",
            nullable = false,
            length = 30
    )
    private BillingItemType itemType;

    @Column(name = "item_name", length = 255)
    private String itemName;

    @Column(name = "source_reference_id", length = 100)
    private String sourceReferenceId;

    @Column(name = "quantity", precision = 19, scale = 2)
    private BigDecimal quantity;

    @Column(name = "rate", precision = 19, scale = 2)
    private BigDecimal rate;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "work_date")
    private LocalDate workDate;

    @Column(name = "role", length = 100)
    private String role;
}
