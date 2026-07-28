package com.AccountReceivableManagement.entity.billing_data_acquisition;

import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One acquired, already-validated billing line within a {@link BillingSnapshot}
 * — a timesheet entry today, a milestone/expense/retainer charge in later
 * stories. {@code sourceReferenceId} traces the line back to its origin-system
 * record; which system that is follows from {@code itemType}.
 */
@Entity
@Table(name = "billing_snapshot_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingSnapshotItem {

    @Id
    @GeneratedValue
    @Column(name = "billing_snapshot_item_id")
    private UUID billingSnapshotItemId;

    @ManyToOne
    @JoinColumn(name = "billing_snapshot_id", nullable = false)
    private BillingSnapshot billingSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 30)
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
}
