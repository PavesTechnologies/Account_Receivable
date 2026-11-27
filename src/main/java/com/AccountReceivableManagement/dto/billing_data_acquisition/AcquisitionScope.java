package com.AccountReceivableManagement.dto.billing_data_acquisition;

import java.util.UUID;

/**
 * How source records already claimed by a Billing Snapshot are treated while
 * acquiring.
 *
 * @param owningSnapshotId   the snapshot being rebuilt, whose own claims do not
 *                           count as "already billed"; {@code null} otherwise
 * @param skipAlreadyClaimed {@code true}: records (timesheets, expenses) already
 *                           claimed by another live snapshot are left out - only
 *                           newly eligible, unclaimed records are acquired
 *                           (incremental billing). {@code false}: such a record
 *                           fails the acquisition (the original single-snapshot
 *                           behaviour).
 */
public record AcquisitionScope(UUID owningSnapshotId, boolean skipAlreadyClaimed) {

    /** Original behaviour: an already-claimed record fails the acquisition. */
    public static final AcquisitionScope FAIL_ON_CLAIMED = new AcquisitionScope(null, false);

    /** A new incremental batch: only unclaimed records. */
    public static AcquisitionScope newBatch() {
        return new AcquisitionScope(null, true);
    }

    /** Rebuilding {@code snapshotId}: its own records plus unclaimed ones; other batches' records are left out. */
    public static AcquisitionScope rebuildOf(UUID snapshotId) {
        return new AcquisitionScope(snapshotId, true);
    }
}
