-- Epic 4 - Tool / Software / License Billing (Phase 2, Story 4.1)
-- Reference DDL for tool_catalog - now Tool Pricing Configuration.
--
-- RMS owns all Software/Tool/License asset master data. This table no
-- longer authors assets (no more tool_code/tool_name business keys) - it
-- only stores AR's commercial pricing for an asset identified by asset_id.
-- asset_code/asset_name are display-only snapshots supplied by the caller
-- from RMS, not editable business keys, and carry no uniqueness constraint
-- of their own (only asset_id does).
--
-- NOTE: This project has no Flyway/Liquibase migration runner; schema is
-- managed via `spring.jpa.hibernate.ddl-auto=update` (see application.properties).
-- Hibernate will create/update this table automatically from the
-- ToolCatalog entity on application startup. This script is a reference
-- only, kept in sync with the entity for documentation and manual
-- environment setup (dialect: MySQL, matching spring.jpa.database-platform).

CREATE TABLE IF NOT EXISTS tool_catalog (
    tool_id                     CHAR(36)      NOT NULL,
    asset_id                    CHAR(36)      NOT NULL,
    asset_code                  VARCHAR(50)   NOT NULL,
    asset_name                  VARCHAR(200)  NOT NULL,
    description                 VARCHAR(500)      NULL,
    billing_basis               VARCHAR(20)   NOT NULL,
    unit_price                  DECIMAL(18,2) NOT NULL,
    currency_id                 CHAR(36)      NOT NULL,
    effective_from              DATE              NULL,
    effective_to                DATE              NULL,
    is_active                   TINYINT(1)    NOT NULL DEFAULT 1,
    created_at                  DATETIME      NOT NULL,
    updated_at                  DATETIME          NULL,
    PRIMARY KEY (tool_id),
    CONSTRAINT uk_tool_catalog_asset_id UNIQUE (asset_id),
    CONSTRAINT fk_tool_catalog_currency
        FOREIGN KEY (currency_id) REFERENCES currency_master (currency_id)
);
