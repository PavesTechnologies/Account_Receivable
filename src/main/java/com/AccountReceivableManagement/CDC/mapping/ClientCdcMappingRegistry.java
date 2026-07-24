package com.AccountReceivableManagement.CDC.mapping;

import com.AccountReceivableManagement.Entity_Enums.client.ClientType;
import com.AccountReceivableManagement.Entity_Enums.client.DeliveryModel;
import com.AccountReceivableManagement.Entity_Enums.client.PriorityLevel;
import com.AccountReceivableManagement.Entity_Enums.client.RecordStatus;

import java.util.HashMap;
import java.util.Map;

public class ClientCdcMappingRegistry {
    public static final Map<String, ColumnMapping> RMS_TO_AR = new HashMap<>();

    static {

        RMS_TO_AR.put(
                "client_id",
                new ColumnMapping(
                        "client_id",
                        "clientId",
                        FieldType.UUID,
                        null
                )
        );

        RMS_TO_AR.put(
                "client_name",
                new ColumnMapping(
                        "client_name",
                        "clientName",
                        FieldType.STRING,
                        null
                )
        );

        RMS_TO_AR.put(
                "client_type",
                new ColumnMapping(
                        "client_type",
                        "clientType",
                        FieldType.ENUM,
                        ClientType.class
                )
        );

        RMS_TO_AR.put(
                "priority_level",
                new ColumnMapping(
                        "priority_level",
                        "priorityLevel",
                        FieldType.ENUM,
                        PriorityLevel.class
                )
        );

        RMS_TO_AR.put(
                "delivery_model",
                new ColumnMapping(
                        "delivery_model",
                        "deliveryModel",
                        FieldType.ENUM,
                        DeliveryModel.class
                )
        );

        RMS_TO_AR.put(
                "country_name",
                new ColumnMapping(
                        "country_name",
                        "countryName",
                        FieldType.STRING,
                        null
                )
        );

        RMS_TO_AR.put(
                "default_timezone",
                new ColumnMapping(
                        "default_timezone",
                        "defaultTimezone",
                        FieldType.STRING,
                        null
                )
        );

        RMS_TO_AR.put(
                "status",
                new ColumnMapping(
                        "status",
                        "status",
                        FieldType.ENUM,
                        RecordStatus.class
                )
        );

        RMS_TO_AR.put(
                "sla",
                new ColumnMapping(
                        "sla",
                        "sla",
                        FieldType.BOOLEAN,
                        null
                )
        );

        RMS_TO_AR.put(
                "compliance",
                new ColumnMapping(
                        "compliance",
                        "compliance",
                        FieldType.BOOLEAN,
                        null
                )
        );

        RMS_TO_AR.put(
                "escalation_contact",
                new ColumnMapping(
                        "escalation_contact",
                        "escalationContact",
                        FieldType.BOOLEAN,
                        null
                )
        );

        RMS_TO_AR.put(
                "assets",
                new ColumnMapping(
                        "assets",
                        "assets",
                        FieldType.BOOLEAN,
                        null
                )
        );

        RMS_TO_AR.put(
                "created_at",
                new ColumnMapping(
                        "created_at",
                        "createdAt",
                        FieldType.LOCAL_DATE_TIME,
                        null
                )
        );

        RMS_TO_AR.put(
                "updated_at",
                new ColumnMapping(
                        "updated_at",
                        "updatedAt",
                        FieldType.LOCAL_DATE_TIME,
                        null
                )
        );

    }

    private ClientCdcMappingRegistry() {
    }
}
