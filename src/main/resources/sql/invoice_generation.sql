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
-- Invoice status lifecycle (Phase 2 adds the REJECTED / resubmission cycle):
--   GENERATED -> PENDING_APPROVAL -> APPROVED
--                                 -> REJECTED -> (correction) -> PENDING_APPROVAL -> APPROVED
--
-- Phase 2B adds a correction-refresh operation for a REJECTED invoice
-- (POST /{invoiceId}/refresh-after-correction): it re-copies subtotal,
-- total_tax_amount, grand_total, billing_period_*, currency_code,
-- payment_term_code, due_date, invoice_item, and invoice_tax_component from
-- the invoice's already-corrected, authoritative billing_snapshot /
-- tax_calculation. invoice_id, invoice_number, and status (REJECTED) are
-- never touched by it - no new invoice_number is generated and no row is
-- added to this table. Resubmission (submit-for-approval) from REJECTED is
-- only permitted once a CORRECTED history entry (see below) exists after
-- the latest REJECTED entry for that invoice.
--
-- Phase 2C adds a non-financial correction operation for a REJECTED invoice
-- (PATCH /{invoiceId}/non-financial-correction): it updates only
-- client_name and project_name on the existing invoice row (both columns
-- already exist - no schema change). invoice_id, invoice_number, status,
-- and every financial column/relationship (subtotal, total_tax_amount,
-- grand_total, invoice_item, invoice_tax_component, billing_snapshot_id,
-- tax_calculation_id, billing period, currency, payment term, due date) are
-- never touched by it. Like Phase 2B's refresh-after-correction, it records
-- a CORRECTED (REJECTED -> REJECTED) history entry (distinguished only by
-- its comment) so the same correctionRequired derivation and
-- submit-for-approval gate apply to both correction paths.
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

-- Audit trail of Invoice status transitions (submit, approve, reject,
-- resubmit-after-rejection) plus CORRECTED, a REJECTED -> REJECTED entry
-- recorded when a rejected invoice's financial snapshot is refreshed from
-- corrected billing/tax data (status does not change). No foreign key to
-- invoice (invoice_id is a plain reference, validated only at the
-- application layer) - this table only records history, it does not own or
-- cascade with invoice, matching the software_billing_history convention.
-- comment stays nullable here even though REJECTED requires a non-blank
-- value - that requirement is enforced at the service layer, not the
-- schema. action/previous_status/new_status stay VARCHAR(20) - "CORRECTED"
-- (9 chars) fits without any column-length change.
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
