package com.AccountReceivableManagement.entity_enums.project;

public enum ProjectStatus {
    NOT_STARTED,
    IN_PROGRESS,
    ON_HOLD,
    COMPLETED,
    ACTIVE,
    CANCELLED,
    DELAYED,
    // PMS project status values (pms.projects.status: ACTIVE, ARCHIVED, PLANNING, COMPLETED); kept as-is, not mapped.
    PLANNING,
    ARCHIVED
}
