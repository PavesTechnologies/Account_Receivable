package com.AccountReceivableManagement.cdc.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CdcEventPayload {

    private String operation;
    private String tableName;
    private String entityType;
    private String entityId;
    private Map<String, Object> before;
    private Map<String, Object> after;
    private Map<String, Object> source;
    private String connectorName;
}
