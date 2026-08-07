-- Epic 4 (Revised Architecture) - Phase 6.
-- Reference DDL for software_billing_history.
--
-- Audit trail proving a given RMS asset was already billed for a given
-- billing period. No foreign key to RMS (asset_id is a plain reference,
-- validated only at the application layer) and no foreign key to
-- billing_snapshot (billing_snapshot_id is likewise a plain reference) -
-- this table only records history, it does not own or cascade with either.
--
-- NOTE: This project has no Flyway/Liquibase migration runner; schema is
-- managed via `spring.jpa.hibernate.ddl-auto=update` (see application.properties).
-- Hibernate will create/update this table automatically from the
-- SoftwareBillingHistory entity on application startup. This script is a
-- reference only, kept in sync with the entity for documentation and manual
-- environment setup (dialect: MySQL, matching spring.jpa.database-platform).

CREATE TABLE IF NOT EXISTS software_billing_history (
    history_id                  CHAR(36)      NOT NULL,
    asset_id                    CHAR(36)      NOT NULL,
    billing_snapshot_id         CHAR(36)      NOT NULL,
    invoice_number               VARCHAR(30)       NULL,
    billing_period_start        DATE          NOT NULL,
    billing_period_end          DATE          NOT NULL,
    quantity                    DECIMAL(19,2)     NULL,
    amount                       DECIMAL(19,2)     NULL,
    currency_code                VARCHAR(10)       NULL,
    billed_at                    DATETIME      NOT NULL,
    created_at                   DATETIME      NOT NULL,
    PRIMARY KEY (history_id),
    CONSTRAINT uk_software_billing_history_asset_period
        UNIQUE (asset_id, billing_period_start, billing_period_end)
);
