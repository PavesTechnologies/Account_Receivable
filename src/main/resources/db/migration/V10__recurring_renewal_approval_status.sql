-- =====================================================================
-- V10: approval state of a recurring renewal term (audit finding G-07)
-- =====================================================================
-- A renewal (RecurringBillingServiceImpl.renew) is a new row of
-- billing_subscription_configuration linked to the term it renews
-- (renewed_from_id). It is created inactive and PENDING_APPROVAL and becomes
-- an active, billable term only when its billing configuration is approved
-- through the existing approval workflow; a rejection marks it REJECTED.
-- is_active alone cannot tell a pending renewal from a deactivated term.
--
-- Additive and nullable: existing rows (original terms and renewals created
-- before this change) keep NULL and behave exactly as before. Hibernate
-- (ddl-auto=update) adds the same column; this script is for environments
-- where schema changes are applied manually (see AUTOMATIC_BILLING_RUNBOOK.md §7).
--
-- Prerequisite check - renewals created before this change that are still
-- active (they were never approved; review them with Finance):
--   SELECT BIN_TO_UUID(subscription_configuration_id), BIN_TO_UUID(renewed_from_id),
--          recurring_start_date, recurring_end_date, is_active
--   FROM billing_subscription_configuration WHERE renewed_from_id IS NOT NULL;

ALTER TABLE billing_subscription_configuration
    ADD COLUMN renewal_approval_status VARCHAR(30) NULL;
