package com.AccountReceivableManagement.dto.billing_data_acquisition;

import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One acquisition batch (Billing Snapshot) already recorded for a billing period, with its invoice if any. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExistingBillingBatchDto {

    private UUID snapshotId;
    /** The snapshot's own billing period. */
    private LocalDate billingPeriodStart;
    private LocalDate billingPeriodEnd;
    /** False when the snapshot's period is not one of the configuration's billing periods. */
    private boolean periodAligned;
    private String snapshotNumber;
    private Integer batchSequence;
    private BillingSnapshotStatus snapshotStatus;
    private BigDecimal totalAmount;
    private int timesheetCount;
    private int expenseCount;
    private LocalDateTime createdDate;

    /** Null when no invoice has been generated from this batch yet. */
    private UUID invoiceId;
    private String invoiceNumber;
    private String invoiceStatus;
}
