package com.AccountReceivableManagement.dto.billing_data_acquisition;

import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;

/**
 * Outcome of a snapshot rebuild (the REJECTED-invoice financial correction).
 *
 * @param snapshot         the rebuilt snapshot
 * @param repricingSummary concise, bounded description of how the rebuild changed the pricing (line counts and
 *                         previous/revised totals); short enough for an approval-history comment
 */
public record SnapshotRebuildResult(BillingSnapshot snapshot, String repricingSummary) {
}
