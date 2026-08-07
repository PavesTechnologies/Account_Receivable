-- Epic 4 - Tool / Software / License Billing (Phase 3, Story 4.2)
-- Reference DDL for project_tool_assignment.
--
-- NOTE: This project has no Flyway/Liquibase migration runner; schema is
-- managed via `spring.jpa.hibernate.ddl-auto=update` (see application.properties).
-- Hibernate will create/update this table automatically from the
-- ProjectToolAssignment entity on application startup. This script is a
-- reference only, kept in sync with the entity for documentation and manual
-- environment setup (dialect: MySQL, matching spring.jpa.database-platform).
--
-- project_id references project_master_reference.pms_project_id (BIGINT),
-- the same CDC-replicated project reference already used by
-- BillingConfiguration - no new Project table introduced.
--
-- The "no overlapping active assignment for the same project+tool" rule is
-- enforced in the service layer (date-range overlap cannot be expressed as a
-- simple UNIQUE constraint), consistent with how BillingConfiguration's
-- "one active configuration per project" rule is also enforced in code.
--
-- No billing_basis column here: it is not duplicated from tool_catalog.
-- Each assignment inherits its Billing Basis from tool_catalog.billing_basis
-- via the tool_id FK, so it always reflects the tool's current value.

CREATE TABLE IF NOT EXISTS project_tool_assignment (
    assignment_id                CHAR(36)      NOT NULL,
    project_id                   BIGINT        NOT NULL,
    tool_id                      CHAR(36)      NOT NULL,
    quantity                     INT           NOT NULL,
    remarks                      VARCHAR(500)      NULL,
    start_date                   DATE          NOT NULL,
    end_date                     DATE              NULL,
    is_active                    TINYINT(1)    NOT NULL DEFAULT 1,
    created_at                   DATETIME      NOT NULL,
    updated_at                   DATETIME          NULL,
    PRIMARY KEY (assignment_id),
    CONSTRAINT fk_project_tool_assignment_project
        FOREIGN KEY (project_id) REFERENCES project_master_reference (pms_project_id),
    CONSTRAINT fk_project_tool_assignment_tool
        FOREIGN KEY (tool_id) REFERENCES tool_catalog (tool_id)
);
