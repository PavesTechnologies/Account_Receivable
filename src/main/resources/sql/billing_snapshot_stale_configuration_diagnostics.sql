-- Diagnostics (READ-ONLY): billing snapshots that belong to a different Billing
-- Configuration than the one now active for the same project.
--
-- billing_snapshot is unique per (project_id, billing_period_start, billing_period_end),
-- not per configuration. A snapshot left behind by an earlier configuration (for
-- example after a partial database cleanup) therefore occupies the period a new
-- configuration wants to acquire. Before the fix POST /api/v1/billing-snapshots and
-- GET /api/v1/billing-snapshots/by-period returned that snapshot - with its
-- INVOICED status - for the new configuration. They now reject / hide it.
--
-- Nothing here modifies data. Dialect: MySQL. No cleanup is scripted on purpose:
-- decide per row using the invoice column below.

-- 1. Snapshots whose configuration is not the project's current ACTIVE/APPROVED one,
--    with the invoice (if any) that makes the period genuinely billed.
SELECT s.billing_snapshot_id,
       s.snapshot_number,
       s.status                      AS snapshot_status,
       s.project_id,
       s.billing_period_start,
       s.billing_period_end,
       s.total_amount,
       s.billing_configuration_id    AS snapshot_configuration_id,
       cur.billing_configuration_id  AS current_configuration_id,
       (old.billing_configuration_id IS NULL) AS snapshot_configuration_deleted,
       i.invoice_id,
       i.invoice_number
FROM billing_snapshot s
JOIN billing_configuration cur
       ON cur.project_id = s.project_id
      AND cur.approval_status = 'APPROVED'
      AND cur.billing_status = 'ACTIVE'
      AND cur.billing_configuration_id <> s.billing_configuration_id
LEFT JOIN billing_configuration old ON old.billing_configuration_id = s.billing_configuration_id
LEFT JOIN invoice i ON i.billing_snapshot_id = s.billing_snapshot_id
ORDER BY s.created_date DESC;

-- 2. INVOICED snapshots with no invoice row at all (status says billed, nothing backs it).
SELECT s.billing_snapshot_id, s.snapshot_number, s.project_id,
       s.billing_period_start, s.billing_period_end, s.billing_configuration_id
FROM billing_snapshot s
LEFT JOIN invoice i ON i.billing_snapshot_id = s.billing_snapshot_id
WHERE s.status = 'INVOICED' AND i.invoice_id IS NULL;

-- 3. Snapshots pointing at a configuration that no longer exists.
SELECT s.billing_snapshot_id, s.snapshot_number, s.status, s.project_id, s.billing_configuration_id
FROM billing_snapshot s
LEFT JOIN billing_configuration bc ON bc.billing_configuration_id = s.billing_configuration_id
WHERE bc.billing_configuration_id IS NULL;

-- Resolution guidance (apply manually, after taking a backup):
--   * Row has an invoice (query 1/invoice_id not null): the period is really billed;
--     the new configuration must use a different billing period. Do not delete.
--   * Row has no invoice and a deleted configuration (queries 2/3): it is stale test
--     data. Delete in FK order - billing_snapshot_item, tax_calculation (+components),
--     billing_acquisition, then billing_snapshot - or ask for the exact statements for
--     the specific snapshot_number before running anything.
