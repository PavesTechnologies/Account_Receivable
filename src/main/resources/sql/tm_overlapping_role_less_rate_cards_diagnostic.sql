-- =========================================================
-- READ-ONLY diagnostic: overlapping active role-less (Standard) T&M rate cards
-- =========================================================
-- Standard billing resolves ONE role-less card per work date and rejects the timesheet when two match, and
-- creating/updating/approving such overlaps is now refused. This lists configurations that already hold them.
--
-- SELECT only: no INSERT/UPDATE/DELETE/DDL. Still, run it ONLY against an approved isolated/staging copy of
-- the data (never the shared/production database without explicit authorization), with a read-only account:
--   mysql --database=<staging_ar_db> --user=<readonly_user> -p < tm_overlapping_role_less_rate_cards_diagnostic.sql
--
-- Rows: one per overlapping PAIR of cards (a_*, b_*); NULL from/to = open-ended. Covers configurations whose
-- pricing_model is STANDARD or NULL (NULL is billed like Standard); ROLE_BASED is excluded because role-less
-- cards are never used there. ids are BINARY(16); BIN_TO_UUID() needs MySQL 8.0.
-- Review before rollout: for every row decide, with the business owner, which card is the intended one and
-- end-date or soft-delete the other through the application (so approval invalidation applies) - do not edit
-- rows directly. Pay special attention to approved/ACTIVE configurations: they will fail at the next snapshot.
-- =========================================================
SELECT BIN_TO_UUID(c.billing_configuration_id) AS billing_configuration_id,
       c.pricing_model, c.approval_status, c.billing_status,
       BIN_TO_UUID(a.rate_card_id) AS a_rate_card_id, a.effective_from AS a_from, a.effective_to AS a_to, a.rate AS a_rate,
       a.created_at AS a_created_at,
       BIN_TO_UUID(b.rate_card_id) AS b_rate_card_id, b.effective_from AS b_from, b.effective_to AS b_to, b.rate AS b_rate,
       b.created_at AS b_created_at
FROM billing_tm_rate_card a
JOIN billing_tm_rate_card b
  ON b.billing_configuration_id = a.billing_configuration_id AND a.rate_card_id < b.rate_card_id
JOIN billing_configuration c ON c.billing_configuration_id = a.billing_configuration_id
WHERE a.is_active = 1 AND b.is_active = 1
  AND (a.role_name IS NULL OR TRIM(a.role_name) = '') AND (b.role_name IS NULL OR TRIM(b.role_name) = '')
  AND (c.pricing_model IS NULL OR c.pricing_model <> 'ROLE_BASED')
  AND (a.effective_from IS NULL OR b.effective_to IS NULL OR a.effective_from <= b.effective_to)
  AND (b.effective_from IS NULL OR a.effective_to IS NULL OR b.effective_from <= a.effective_to)
ORDER BY c.billing_configuration_id, a.effective_from, b.effective_from;
