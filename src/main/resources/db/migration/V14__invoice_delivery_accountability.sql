-- =====================================================================
-- V14: invoice delivery accountability and resolution (G-19/G-20)
-- =====================================================================
-- Who requested each delivery, and the operator resolution of a delivery
-- whose outcome is unknown (new status UNCERTAIN, or PENDING left behind)
-- to SENT or FAILED after checking the sending mailbox.
-- invoice_delivery.status is VARCHAR(20); UNCERTAIN needs no change there.
--
-- Additive and nullable; existing rows keep NULL.

ALTER TABLE invoice_delivery
    ADD COLUMN requested_by          VARCHAR(100) NULL,
    ADD COLUMN requested_by_user_id  BIGINT       NULL,
    ADD COLUMN resolved_at           DATETIME(6)  NULL,
    ADD COLUMN resolved_by           VARCHAR(100) NULL,
    ADD COLUMN resolved_by_user_id   BIGINT       NULL,
    ADD COLUMN resolution_reason     VARCHAR(500) NULL;
