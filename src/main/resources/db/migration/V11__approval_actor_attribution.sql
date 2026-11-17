-- =====================================================================
-- V11: approval actors and maker-checker (audit findings G-08 / UAT-D1)
-- =====================================================================
-- Billing configuration: the user who submitted it for approval (maker) and
-- the user who last approved or rejected it (checker), from the authenticated
-- UMS identity. Invoice approval history: the acting user's stable UMS id next
-- to the readable action_by label, which until now always said "SYSTEM".
--
-- Additive and nullable; existing rows keep NULL (their actor was never
-- recorded). Hibernate (ddl-auto=update) adds the same columns; this script is
-- for environments where schema changes are applied manually
-- (AUTOMATIC_BILLING_RUNBOOK.md §7).

ALTER TABLE billing_configuration
    ADD COLUMN submitted_by_user_id BIGINT NULL,
    ADD COLUMN approval_decided_by_user_id BIGINT NULL,
    ADD COLUMN approval_decided_by VARCHAR(100) NULL,
    ADD COLUMN approval_decided_at DATETIME(6) NULL;

ALTER TABLE invoice_approval_history
    ADD COLUMN action_by_user_id BIGINT NULL;
