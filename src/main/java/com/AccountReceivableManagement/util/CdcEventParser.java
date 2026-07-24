package com.AccountReceivableManagement.util;

import com.AccountReceivableManagement.CDC.payload.CdcEventPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.debezium.engine.ChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CdcEventParser {

    private final ObjectMapper objectMapper;

    public CdcEventPayload parse(ChangeEvent<String, String> event) {
        String jsonValue = event.value();
        if (jsonValue == null) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(jsonValue);
            JsonNode payload = root.has("payload") ? root.get("payload") : root;

            // Ignore heartbeat events
            if (!payload.has("op")) {
                log.debug("Heartbeat event received. Ignoring.");
                return null;
            }

            JsonNode opNode = payload.get("op");

            if (opNode == null) {
                log.debug("Heartbeat event received. Ignoring.");
                return null;
            }

            String operation = opNode.asText();
            JsonNode source = payload.get("source");
            String tableName = source.get("table").asText();
            String databaseName = source.get("db").asText();
            String entityType = databaseName.toUpperCase() + "-" + tableName.toUpperCase();

            JsonNode data = "d".equals(operation) || "delete".equals(operation)
                    ? payload.get("before")
                    : payload.get("after");

            if (data == null || data.isNull()) {
                log.warn("CDC event data is null for operation {}. Event: {}", operation, jsonValue);
                return null;
            }

            String entityId = extractEntityId(data, tableName);

            return CdcEventPayload.builder()
                    .operation(operation)
                    .tableName(tableName)
                    .entityType(entityType)
                    .entityId(entityId)
                    .before(jsonNodeToMap(payload.get("before")))
                    .after(jsonNodeToMap(payload.get("after")))
                    .source(jsonNodeToMap(source))
                    .connectorName(source.get("name").asText())
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse CDC event. Event: {}", jsonValue, e);
            return null;
        }
    }

    private String extractEntityId(JsonNode data, String tableName) {
        if ("projects".equalsIgnoreCase(tableName)) {
            JsonNode projectId = data.get("id");
            if (projectId != null) {
                return projectId.asText();
            }
        }

        // Fallback for other tables
        JsonNode clientId = data.get("client_id");
        if (clientId != null) {
            return clientId.asText();
        }

        JsonNode id = data.get("id");
        if (id != null) {
            return id.asText();
        }

        throw new IllegalArgumentException("Primary key not found in CDC event data for table " + tableName);
    }

    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            map.put(entry.getKey(), jsonNodeToObject(entry.getValue()));
        }
        return map;
    }

    private Object jsonNodeToObject(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            return jsonNodeToMap(node);
        }
        if (node.isArray()) {
            // Handle arrays if needed
            return node.toString();
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isInt()) {
            return node.asInt();
        }
        if (node.isLong()) {
            return node.asLong();
        }
        if (node.isDouble()) {
            return node.asDouble();
        }
        return node.toString();
    }
}
