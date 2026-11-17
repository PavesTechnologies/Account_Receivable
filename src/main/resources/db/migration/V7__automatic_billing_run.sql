-- =========================================================
-- Automatic Billing Run (automation execution / audit record)
-- =========================================================
-- One row per billing event that automatic billing works on: a
-- Billing Occurrence (billing_schedule) of Fixed Price, Milestone /
-- Installment or Recurring billing, or a Time & Material billing period
-- (which has no persisted occurrence). The row lives across every
-- processing attempt; attempt_count counts them.
--
-- uk_automatic_billing_run_period is the business key - the same
-- (configuration, period) key as uk_billing_schedule_period and
-- uk_billing_acq_config_period - so the same billing event can never get
-- a second run, however many scheduler passes or instances create it.
--
-- billing_schedule_id / billing_snapshot_id / invoice_id carry no foreign
-- key, the same as invoice.billing_snapshot_id and
-- billing_acquisition.snapshot_id: configuration reconciliation
-- hard-deletes SCHEDULED occurrences, which a foreign key would block.
--
-- New table only. No existing data is read, changed or deleted.
-- Hibernate (ddl-auto=update) also creates this table from the
-- AutomaticBillingRun entity; the IF NOT EXISTS makes this script a no-op
-- in that case.
-- =========================================================

-- STEP 1 - Check id column types. They must match billing_configuration's
-- (Hibernate 6 maps java.util.UUID to BINARY(16) on MySQL); if this
-- environment's billing_configuration_id differs, adjust the id columns
-- in STEP 2 before running it.
SELECT column_name, column_type
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'billing_configuration'
  AND column_name = 'billing_configuration_id';

-- STEP 2 - Create the table.
CREATE TABLE IF NOT EXISTS automatic_billing_run (
    automatic_billing_run_id  BINARY(16)    NOT NULL,
    version                   BIGINT        NOT NULL,
    billing_configuration_id  BINARY(16)    NOT NULL,
    billing_schedule_id       BINARY(16)        NULL,
    billing_period_start      DATE          NOT NULL,
    billing_period_end        DATE          NOT NULL,
    billing_snapshot_id       BINARY(16)        NULL,
    invoice_id                BINARY(16)        NULL,
    status                    VARCHAR(30)   NOT NULL,
    current_step              VARCHAR(40)       NULL,
    last_completed_step       VARCHAR(40)       NULL,
    attempt_count             INT           NOT NULL,
    claim_token               BINARY(16)        NULL,
    claim_expires_at          DATETIME(6)       NULL,
    failure_step              VARCHAR(40)       NULL,
    failure_reason            VARCHAR(1000)     NULL,
    failed_at                 DATETIME(6)       NULL,
    next_retry_at             DATETIME(6)       NULL,
    started_at                DATETIME(6)       NULL,
    completed_at              DATETIME(6)       NULL,
    created_at                DATETIME(6)   NOT NULL,
    updated_at                DATETIME(6)       NULL,
    PRIMARY KEY (automatic_billing_run_id),
    CONSTRAINT uk_automatic_billing_run_period
        UNIQUE (billing_configuration_id, billing_period_start, billing_period_end),
    CONSTRAINT uk_automatic_billing_run_schedule
        UNIQUE (billing_schedule_id),
    CONSTRAINT fk_automatic_billing_run_configuration
        FOREIGN KEY (billing_configuration_id)
        REFERENCES billing_configuration (billing_configuration_id),
    INDEX idx_automatic_billing_run_status (status, next_retry_at),
    INDEX idx_automatic_billing_run_invoice (invoice_id)
);
