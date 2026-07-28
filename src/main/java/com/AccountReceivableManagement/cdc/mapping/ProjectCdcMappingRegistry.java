package com.AccountReceivableManagement.cdc.mapping;

import com.AccountReceivableManagement.entity_enums.client.DeliveryModel;
import com.AccountReceivableManagement.entity_enums.client.PriorityLevel;
import com.AccountReceivableManagement.entity_enums.project.ProjectDataStatus;
import com.AccountReceivableManagement.entity_enums.project.ProjectStage;
import com.AccountReceivableManagement.entity_enums.project.ProjectStatus;
import com.AccountReceivableManagement.entity_enums.project.RiskLevel;

import java.util.HashMap;
import java.util.Map;

public class ProjectCdcMappingRegistry {
    public static final Map<String, ColumnMapping> PMS_TO_AR = new HashMap<>();

    static {

        PMS_TO_AR.put(
                "id",
                new ColumnMapping(
                        "id",
                        "pmsProjectId",
                        FieldType.LONG,
                        null
                )
        );

        PMS_TO_AR.put(
                "name",
                new ColumnMapping(
                        "name",
                        "projectName",
                        FieldType.STRING,
                        null
                )
        );

        PMS_TO_AR.put(
                "client_id",
                new ColumnMapping(
                        "client_id",
                        "clientId",
                        FieldType.UUID,
                        null
                )
        );

        PMS_TO_AR.put(
                "owner_id",
                new ColumnMapping(
                        "owner_id",
                        "projectManagerId",
                        FieldType.LONG,
                        null
                )
        );

        PMS_TO_AR.put(
                "rm_id",
                new ColumnMapping(
                        "rm_id",
                        "resourceManagerId",
                        FieldType.LONG,
                        null
                )
        );

        PMS_TO_AR.put(
                "delivery_owner_id",
                new ColumnMapping(
                        "delivery_owner_id",
                        "deliveryOwnerId",
                        FieldType.LONG,
                        null
                )
        );

        PMS_TO_AR.put(
                "delivery_model",
                new ColumnMapping(
                        "delivery_model",
                        "deliveryModel",
                        FieldType.ENUM,
                        DeliveryModel.class
                )
        );

        PMS_TO_AR.put(
                "primary_location",
                new ColumnMapping(
                        "primary_location",
                        "primaryLocation",
                        FieldType.STRING,
                        null
                )
        );

        PMS_TO_AR.put(
                "priority_level",
                new ColumnMapping(
                        "priority_level",
                        "priorityLevel",
                        FieldType.ENUM,
                        PriorityLevel.class
                )
        );

        PMS_TO_AR.put(
                "risk_level",
                new ColumnMapping(
                        "risk_level",
                        "riskLevel",
                        FieldType.ENUM,
                        RiskLevel.class
                )
        );

        PMS_TO_AR.put(
                "start_date",
                new ColumnMapping(
                        "start_date",
                        "startDate",
                        FieldType.LOCAL_DATE,
                        null
                )
        );

        PMS_TO_AR.put(
                "end_date",
                new ColumnMapping(
                        "end_date",
                        "endDate",
                        FieldType.LOCAL_DATE,
                        null
                )
        );

        PMS_TO_AR.put(
                "project_budget",
                new ColumnMapping(
                        "project_budget",
                        "projectBudget",
                        FieldType.BIG_DECIMAL,
                        null
                )
        );

        PMS_TO_AR.put(
                "project_budget_currency",
                new ColumnMapping(
                        "project_budget_currency",
                        "projectBudgetCurrency",
                        FieldType.STRING,
                        null
                )
        );

        PMS_TO_AR.put(
                "status",
                new ColumnMapping(
                        "status",
                        "projectStatus",
                        FieldType.ENUM,
                        ProjectStatus.class
                )
        );

        PMS_TO_AR.put(
                "current_stage",
                new ColumnMapping(
                        "current_stage",
                        "lifecycleStage",
                        FieldType.ENUM,
                        ProjectStage.class
                )
        );

        PMS_TO_AR.put(
                "data_status",
                new ColumnMapping(
                        "data_status",
                        "dataStatus",
                        FieldType.ENUM,
                        ProjectDataStatus.class
                )
        );

        PMS_TO_AR.put(
                "updated_at",
                new ColumnMapping(
                        "updated_at",
                        "changedAt",
                        FieldType.LOCAL_DATE_TIME,
                        null
                )
        );

        PMS_TO_AR.put(
                "last_synced_at",
                new ColumnMapping(
                        "last_synced_at",
                        "lastSyncedAt",
                        FieldType.LOCAL_DATE_TIME,
                        null
                )
        );

        PMS_TO_AR.put(
                "created_at",
                new ColumnMapping(
                        "created_at",
                        "createdAt",
                        FieldType.LOCAL_DATE_TIME,
                        null
                )
        );

    }

    private ProjectCdcMappingRegistry() {
    }
}
