-- Invoice Generation and Invoice Approval (Phase 1).
-- Reference DDL for invoice, invoice_item, invoice_tax_component,
-- invoice_approval_history.
--
-- The frozen, final commercial document generated for exactly one
-- billing_snapshot once its tax_calculation is complete. Financial totals
-- (subtotal, total_tax_amount, grand_total) are copied as-is from the
-- persisted tax_calculation - never recalculated. Line items and tax
-- components are copied as-is from billing_snapshot_item and
-- tax_calculation_component respectively.
--
-- Invoice status lifecycle (Phase 1 implements GENERATED -> PENDING_APPROVAL
-- -> APPROVED only; REJECTED is provisioned in the enum/model but not yet
-- reachable):
--   GENERATED -> PENDING_APPROVAL -> APPROVED
--                                 -> REJECTED (future)
--
-- NOTE: This project has no Flyway/Liquibase migration runner; schema is
-- managed via `spring.jpa.hibernate.ddl-auto=update` (see application.properties).
-- Hibernate will create/update these tables automatically from the
-- Invoice/InvoiceItem/InvoiceTaxComponent entities on application startup.
-- This script is a reference only, kept in sync with the entities for
-- documentation and manual environment setup (dialect: MySQL, matching
-- spring.jpa.database-platform).

CREATE TABLE IF NOT EXISTS invoice (
    invoice_id                   CHAR(36)      NOT NULL,
    invoice_number                VARCHAR(30)  NOT NULL,
    billing_snapshot_id           CHAR(36)     NOT NULL,
    -- Human-readable BillingSnapshot.snapshot_number, frozen at generation
    -- time through the billing_snapshot_id relationship (never fabricated).
    billing_snapshot_number       VARCHAR(30)      NULL,
    tax_calculation_id            CHAR(36)     NOT NULL,
    client_id                     CHAR(36)     NOT NULL,
    client_name                   VARCHAR(255)     NULL,
    -- billing_address / gstin_or_tax_id / contact: no source field exists
    -- anywhere in the current data model (Client has none of these). Kept
    -- as nullable columns for Phase 1 schema completeness; always NULL
    -- until a source field is introduced. See the implementation report.
    billing_address               VARCHAR(500)     NULL,
    gstin_or_tax_id                VARCHAR(50)      NULL,
    contact                        VARCHAR(255)     NULL,
    project_id                    BIGINT       NOT NULL,
    project_name                  VARCHAR(255)     NULL,
    billing_period_start          DATE         NOT NULL,
    billing_period_end            DATE         NOT NULL,
    currency_code                  VARCHAR(10)      NULL,
    payment_term_code              VARCHAR(100)     NULL,
    subtotal                      DECIMAL(19,2) NOT NULL,
    total_tax_amount               DECIMAL(19,2) NOT NULL,
    grand_total                    DECIMAL(19,2) NOT NULL,
    invoice_date                  DATE         NOT NULL,
    due_date                       DATE             NULL,
    generated_at                   DATETIME     NOT NULL,
    status                         VARCHAR(20)  NOT NULL,
    created_at                     DATETIME     NOT NULL,
    updated_at                     DATETIME         NULL,
    PRIMARY KEY (invoice_id),
    CONSTRAINT uk_invoice_billing_snapshot UNIQUE (billing_snapshot_id),
    CONSTRAINT uk_invoice_number UNIQUE (invoice_number)
);

CREATE TABLE IF NOT EXISTS invoice_item (
    invoice_item_id                CHAR(36)      NOT NULL,
    invoice_id                     CHAR(36)      NOT NULL,
    item_type                       VARCHAR(30)  NOT NULL,
    item_name                       VARCHAR(255)     NULL,
    source_reference_id             VARCHAR(100)     NULL,
    quantity                        DECIMAL(19,2)     NULL,
    rate                             DECIMAL(19,2)     NULL,
    amount                           DECIMAL(19,2)     NULL,
    work_date                        DATE             NULL,
    role                             VARCHAR(100)     NULL,
    PRIMARY KEY (invoice_item_id),
    CONSTRAINT fk_invoice_item_invoice FOREIGN KEY (invoice_id) REFERENCES invoice (invoice_id)
);

CREATE TABLE IF NOT EXISTS invoice_tax_component (
    invoice_tax_component_id        CHAR(36)      NOT NULL,
    invoice_id                      CHAR(36)      NOT NULL,
    tax_type_id                     CHAR(36)      NOT NULL,
    tax_type_code                    VARCHAR(50)  NOT NULL,
    tax_type_name                    VARCHAR(100) NOT NULL,
    applied_rate                     DECIMAL(10,4) NOT NULL,
    tax_amount                       DECIMAL(19,2) NOT NULL,
    applicability_type               VARCHAR(30)  NOT NULL,
    PRIMARY KEY (invoice_tax_component_id),
    CONSTRAINT fk_invoice_tax_component_invoice FOREIGN KEY (invoice_id) REFERENCES invoice (invoice_id)
);

-- Audit trail of Invoice status transitions (submit, approve - later
-- reject). No foreign key to invoice (invoice_id is a plain reference,
-- validated only at the application layer) - this table only records
-- history, it does not own or cascade with invoice, matching the
-- software_billing_history convention. comment stays nullable here even
-- though the future REJECTED action will require it - that requirement is
-- enforced at the service layer, not the schema.
CREATE TABLE IF NOT EXISTS invoice_approval_history (
    invoice_approval_history_id     CHAR(36)      NOT NULL,
    invoice_id                      CHAR(36)      NOT NULL,
    previous_status                  VARCHAR(20)  NOT NULL,
    new_status                       VARCHAR(20)  NOT NULL,
    action                            VARCHAR(20)  NOT NULL,
    action_by                        VARCHAR(100) NOT NULL,
    action_at                        DATETIME     NOT NULL,
    comment                          VARCHAR(500)     NULL,
    PRIMARY KEY (invoice_approval_history_id)
);
