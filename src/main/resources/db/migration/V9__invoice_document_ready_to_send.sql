-- =========================================================
-- Finalized invoice documents (READY_TO_SEND)
-- =========================================================
-- An approved invoice's PDF is now generated once, stored here, and the
-- invoice moves APPROVED -> READY_TO_SEND in the same transaction. The
-- stored document is what is downloaded and sent to the client.
--
-- invoice.status is already VARCHAR(20) (see Invoice.status), so the new
-- READY_TO_SEND value needs no change there. Existing invoices keep their
-- status: previously approved invoices stay APPROVED until their document
-- is generated (POST /api/v1/invoices/{id}/document).
--
-- invoice_id carries no foreign key, as invoice_delivery.invoice_id and
-- invoice_approval_history.invoice_id don't; uk_invoice_document_invoice
-- keeps it to one document per invoice.
--
-- New table only. No existing data is read, changed or deleted.
-- Hibernate (ddl-auto=update) also creates this table from the
-- InvoiceDocument entity; IF NOT EXISTS makes this script a no-op then.
-- =========================================================

-- STEP 1 - Check the id column type. It must match invoice.invoice_id
-- (Hibernate 6 maps java.util.UUID to BINARY(16) on MySQL); adjust STEP 2
-- if this environment differs.
SELECT column_name, column_type
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'invoice'
  AND column_name = 'invoice_id';

-- STEP 2 - Create the table.
CREATE TABLE IF NOT EXISTS invoice_document (
    invoice_document_id  BINARY(16)    NOT NULL,
    invoice_id           BINARY(16)    NOT NULL,
    file_name            VARCHAR(150)  NOT NULL,
    content_type         VARCHAR(50)   NOT NULL,
    content              LONGBLOB      NOT NULL,
    size_bytes           BIGINT        NOT NULL,
    checksum_sha256      VARCHAR(64)   NOT NULL,
    generated_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (invoice_document_id),
    CONSTRAINT uk_invoice_document_invoice UNIQUE (invoice_id)
);
