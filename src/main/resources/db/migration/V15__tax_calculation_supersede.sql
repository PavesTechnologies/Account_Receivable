-- =====================================================================
-- V15: superseded tax calculations (BD-07 recovery)
-- =====================================================================
-- An occurrence whose configuration currency or tax region changed after
-- tax was calculated cannot be invoiced (G-12). An authorized operator can
-- recalculate its tax while it is not invoiced: the old calculation is kept
-- as history (billing_schedule_id moved to superseded_billing_schedule_id,
-- who/when/why recorded) and a new one is created for the occurrence.
--
-- Additive and nullable; existing rows keep NULL.

ALTER TABLE tax_calculation
    ADD COLUMN superseded_at                  DATETIME(6)  NULL,
    ADD COLUMN superseded_billing_schedule_id BINARY(16)   NULL,
    ADD COLUMN superseded_by                  VARCHAR(100) NULL,
    ADD COLUMN superseded_by_user_id          BIGINT       NULL,
    ADD COLUMN superseded_reason              VARCHAR(500) NULL;
