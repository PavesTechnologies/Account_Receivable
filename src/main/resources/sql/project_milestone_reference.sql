-- =====================================================
-- PMS project milestones mirror (CDC: pms.project_milestones -> project_milestone_reference)
-- Hibernate ddl-auto=update creates this table; run manually where it does not.
-- =====================================================

CREATE TABLE IF NOT EXISTS project_milestone_reference (
    pms_milestone_id        BIGINT        NOT NULL,
    pms_project_id          BIGINT        NULL,
    milestone_code          VARCHAR(50)   NULL,
    milestone_name          VARCHAR(200)  NULL,
    description             VARCHAR(2000) NULL,
    pms_epic_id             BIGINT        NULL,
    planned_start_date      DATE          NULL,
    planned_due_date        DATE          NULL,
    actual_completion_date  DATE          NULL,
    execution_status        VARCHAR(20)   NULL,
    billable                BIT           NULL,
    allocation_percentage   DECIMAL(5,2)  NULL,
    billing_readiness       VARCHAR(30)   NULL,
    source_version          BIGINT        NULL,
    source_deleted          BIT           NOT NULL DEFAULT 0,
    created_at              DATETIME(6)   NULL,
    changed_at              DATETIME(6)   NULL,
    last_synced_at          DATETIME(6)   NULL,
    PRIMARY KEY (pms_milestone_id),
    INDEX idx_pmr_project (pms_project_id),
    INDEX idx_pmr_project_readiness (pms_project_id, billing_readiness)
) ENGINE = InnoDB;

-- =====================================================
-- Verification Queries
-- =====================================================
-- SELECT entity_type, status, COUNT(*) FROM cdc_inbox WHERE entity_type = 'PMS-PROJECT_MILESTONES' GROUP BY entity_type, status;
-- SELECT pms_milestone_id, milestone_code, execution_status, allocation_percentage, billing_readiness, source_version, source_deleted
--   FROM project_milestone_reference WHERE pms_project_id = ? ORDER BY pms_milestone_id;
