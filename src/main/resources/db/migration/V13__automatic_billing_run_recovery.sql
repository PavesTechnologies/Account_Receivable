-- =====================================================================
-- V13: operational recovery of automatic billing runs (G-17/G-18/G-30)
-- =====================================================================
-- automatic_billing_run.requeued_at: when a run was last explicitly
-- re-queued (operator, or configuration re-approval) rather than by the
-- automatic retry policy. A FAILED run re-queued after its last failure is
-- processed even while automatic retry is disabled.
--
-- automatic_billing_run_event: append-only audit of recovery actions
-- (REQUEUED, APPROVAL_RECONCILED), each with the failure in place when it
-- was taken, so a later attempt never erases it, plus actor and reason.
-- run_id carries no foreign key, like the other history tables.
--
-- Additive only; no existing data is read, changed or deleted.

ALTER TABLE automatic_billing_run
    ADD COLUMN requeued_at DATETIME(6) NULL;

CREATE TABLE IF NOT EXISTS automatic_billing_run_event (
    automatic_billing_run_event_id  BINARY(16)     NOT NULL,
    run_id                          BINARY(16)     NOT NULL,
    event_type                      VARCHAR(30)    NOT NULL,
    status_before                   VARCHAR(30)    NOT NULL,
    status_after                    VARCHAR(30)    NOT NULL,
    failure_step                    VARCHAR(40)        NULL,
    failure_reason                  VARCHAR(1000)      NULL,
    failed_at                       DATETIME(6)        NULL,
    attempt_count                   INT                NULL,
    actor                           VARCHAR(100)   NOT NULL,
    actor_user_id                   BIGINT             NULL,
    reason                          VARCHAR(500)       NULL,
    occurred_at                     DATETIME(6)    NOT NULL,
    PRIMARY KEY (automatic_billing_run_event_id),
    INDEX idx_automatic_billing_run_event_run (run_id, occurred_at)
);
