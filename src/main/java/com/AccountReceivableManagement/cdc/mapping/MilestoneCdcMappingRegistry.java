package com.AccountReceivableManagement.cdc.mapping;

import java.util.HashMap;
import java.util.Map;

/**
 * Column mapping for PMS table pms.project_milestones -> AR project_milestone_reference.
 * Columns not listed here (sequence_order, created_by, updated_by, completed_by) are skipped.
 */
public class MilestoneCdcMappingRegistry {
    public static final Map<String, ColumnMapping> PMS_TO_AR = new HashMap<>();

    static {
        put("id", "pmsMilestoneId", FieldType.LONG);
        put("project_id", "pmsProjectId", FieldType.LONG);
        put("epic_id", "pmsEpicId", FieldType.LONG);
        put("milestone_code", "milestoneCode", FieldType.STRING);
        put("name", "milestoneName", FieldType.STRING);
        put("description", "description", FieldType.STRING);
        put("planned_start_date", "plannedStartDate", FieldType.LOCAL_DATE);
        put("planned_due_date", "plannedDueDate", FieldType.LOCAL_DATE);
        put("actual_completion_date", "actualCompletionDate", FieldType.LOCAL_DATE);
        put("execution_status", "executionStatus", FieldType.STRING);
        put("billable", "billable", FieldType.BOOLEAN);
        put("allocation_percentage", "allocationPercentage", FieldType.BIG_DECIMAL);
        put("billing_readiness", "billingReadiness", FieldType.STRING);
        put("version", "sourceVersion", FieldType.LONG);
        put("created_at", "createdAt", FieldType.LOCAL_DATE_TIME);
        put("updated_at", "changedAt", FieldType.LOCAL_DATE_TIME);
    }

    private static void put(String column, String field, FieldType type) {
        PMS_TO_AR.put(column, new ColumnMapping(column, field, type, null));
    }

    private MilestoneCdcMappingRegistry() {
    }
}
