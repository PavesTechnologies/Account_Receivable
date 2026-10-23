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

    /**
     * Frozen copy of {@code BillingSnapshotItem.resourceName} - the TMS
     * employee display name for a TIME_ENTRY line. {@code null} for line
     * types with no resource concept (e.g. FIXED_PRICE), never guessed.
     */
    @Column(name = "resource_name", length = 255)
    private String resourceName;

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

    // ---- T&M rate audit, frozen from the BillingSnapshotItem of a TIME_ENTRY line ----
    // "rate" above stays the effective NET rate and "amount" the final line amount. These fields only describe
    // how the net rate came about; they are never used to recompute amounts. All null on EXPENSE lines (a T&M
    // discount never applies to an expense) and on invoices generated before these columns existed.

    @Column(name = "base_rate", precision = 19, scale = 2)
    private BigDecimal baseRate;

    @Column(name = "discount_type", length = 20)
    private String discountType;

    @Column(name = "discount_value", precision = 19, scale = 2)
    private BigDecimal discountValue;

    /** Discount per rate unit (baseRate - rate), as calculated at acquisition. */
    @Column(name = "discount_amount", precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "discount_period_name", length = 150)
    private String discountPeriodName;
}
