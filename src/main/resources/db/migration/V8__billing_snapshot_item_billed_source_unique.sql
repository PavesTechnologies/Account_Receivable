-- =========================================================
-- One billing claim per TMS timesheet
-- =========================================================
-- BillingAcquisitionValidator rejects a timesheet already carried by
-- another (non-CANCELLED) Billing Snapshot, but that is a read-then-insert
-- check: two snapshots acquired concurrently - automatic and manual, or
-- two overlapping billing periods - can both pass it and both insert the
-- same timesheet. uk_billing_snapshot_item_billed_source makes the second
-- insert fail instead.
--
-- The key is on a new, nullable claim column, not on source_reference_id
-- itself:
--   * NULLs are not compared by a MySQL unique index, so lines that make
--     no claim (tool/software lines, rows written before this change) are
--     unaffected;
--   * releasing a timesheet for billing again (e.g. a future snapshot
--     cancellation) only has to clear billed_source_reference_id - the
--     line's own source_reference_id stays for audit.
--
-- Hibernate (ddl-auto=update) also adds the column and tries to create
-- the constraint from the BillingSnapshotItem mapping; run STEP 1-3
-- either way. STEP 4 is optional.
--
-- This script deletes NO data. STEP 4 only fills the new column.
-- =========================================================

-- STEP 1 - Add the column (skip if Hibernate already added it).
ALTER TABLE billing_snapshot_item
    ADD COLUMN billed_source_reference_id VARCHAR(100) NULL;

-- STEP 2 - Already applied? If this returns a row, skip STEP 3.
SELECT index_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'billing_snapshot_item'
  AND index_name = 'uk_billing_snapshot_item_billed_source';

-- STEP 3 - Add the constraint (succeeds on the all-NULL new column).
ALTER TABLE billing_snapshot_item
    ADD CONSTRAINT uk_billing_snapshot_item_billed_source
        UNIQUE (item_type, billed_source_reference_id);

-- STEP 4a - OPTIONAL backfill, duplicate check. MUST return zero rows
-- before STEP 4b. Any row returned is a timesheet already billed by more
-- than one live snapshot: resolve it with Finance through the normal
-- correction process - do not delete snapshot items from here.
SELECT i.item_type,
       i.source_reference_id,
       COUNT(*)                                     AS snapshot_count,
       GROUP_CONCAT(s.snapshot_number ORDER BY s.created_date) AS snapshot_numbers
FROM billing_snapshot_item i
JOIN billing_snapshot s ON s.billing_snapshot_id = i.billing_snapshot_id
WHERE i.item_type = 'TIME_ENTRY'
  AND i.source_reference_id IS NOT NULL
  AND s.status <> 'CANCELLED'
GROUP BY i.item_type, i.source_reference_id
HAVING COUNT(*) > 1;

-- STEP 4b - OPTIONAL backfill. Extends the database guard to timesheets
-- billed before this change (the validator already covers them). Fails
-- as a whole, changing nothing, if STEP 4a was skipped and duplicates exist.
UPDATE billing_snapshot_item i
JOIN billing_snapshot s ON s.billing_snapshot_id = i.billing_snapshot_id
SET i.billed_source_reference_id = i.source_reference_id
WHERE i.item_type = 'TIME_ENTRY'
  AND i.source_reference_id IS NOT NULL
  AND i.billed_source_reference_id IS NULL
  AND s.status <> 'CANCELLED';
