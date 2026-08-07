-- Epic 4 - Tool / Software / License Billing (Phase 1)
-- Reference DDL for proration_rule_master and ar_project_tool_billing_config.
--
-- NOTE: This project has no Flyway/Liquibase migration runner; schema is
-- managed via `spring.jpa.hibernate.ddl-auto=update` (see application.properties).
-- Hibernate will create/update these tables automatically from the
-- ProrationRuleMaster and ProjectToolBillingConfig entities on application
-- startup. This script is a reference only, kept in sync with the entities
-- for documentation and manual environment setup (dialect: MySQL, matching
-- spring.jpa.database-platform).

-- proration_rule_master follows the same centralized master-data convention
-- as currency_master / billing_type_master / payment_terms_master / etc.
CREATE TABLE IF NOT EXISTS proration_rule_master (
    proration_rule_id           CHAR(36)     NOT NULL,
    proration_rule_code         VARCHAR(20)  NOT NULL,
    proration_rule_name         VARCHAR(100) NOT NULL,
    description                 VARCHAR(500)     NULL,
    is_active                   TINYINT(1)   NOT NULL DEFAULT 1,
    created_at                  DATETIME     NOT NULL,
    updated_at                  DATETIME         NULL,
    PRIMARY KEY (proration_rule_id),
    CONSTRAINT uk_proration_rule_master_code UNIQUE (proration_rule_code)
);

-- project_id references project_master_reference.pms_project_id (BIGINT),
-- the same CDC-replicated project reference used by BillingConfiguration
-- and ProjectToolAssignment - not a locally-generated UUID.
CREATE TABLE IF NOT EXISTS ar_project_tool_billing_config (
    id                          CHAR(36)     NOT NULL,
    project_id                  BIGINT       NOT NULL,
    tool_billing_enabled        TINYINT(1)   NOT NULL DEFAULT 0,
    allow_one_time_charges      TINYINT(1)   NOT NULL DEFAULT 0,
    allow_recurring_charges     TINYINT(1)   NOT NULL DEFAULT 0,
    default_proration_rule_id   CHAR(36)     NOT NULL,
    created_by                  VARCHAR(100)     NULL,
    created_at                  DATETIME     NOT NULL,
    updated_by                  VARCHAR(100)     NULL,
    updated_at                  DATETIME         NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ar_project_tool_billing_config_project_id UNIQUE (project_id),
    CONSTRAINT fk_ar_project_tool_billing_config_proration_rule
        FOREIGN KEY (default_proration_rule_id) REFERENCES proration_rule_master (proration_rule_id),
    CONSTRAINT fk_ar_project_tool_billing_config_project
        FOREIGN KEY (project_id) REFERENCES project_master_reference (pms_project_id)
);
