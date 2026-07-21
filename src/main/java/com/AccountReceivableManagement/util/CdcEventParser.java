package com.AccountReceivableManagement.util;

import com.AccountReceivableManagement.CDC.payload.CdcEventPayload;
import io.debezium.data.Envelope;
import io.debezium.engine.RecordChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CdcEventParser {

    public CdcEventPayload parse(RecordChangeEvent<SourceRecord> event) {
        Struct value = (Struct) event.record().value();
        if (value == null) {
            return null;
        }

        Envelope.Operation operation = Envelope.Operation.forCode(value.getString("op"));
        Struct source = value.getStruct("source");
        String tableName = source.getString("table");
        String entityType = "RMS-" + tableName;

        Struct data = operation == Envelope.Operation.DELETE
                ? value.getStruct("before")
                : value.getStruct("after");

        if (data == null) {
            log.warn("CDC event data is null for operation {}. Record: {}", operation, event.record());
            return null;
        }

        String entityId = extractEntityId(data);

        return CdcEventPayload.builder()
                .operation(operation.code())
                .tableName(tableName)
                .entityType(entityType)
                .entityId(entityId)
                .before(structToMap(value.getStruct("before")))
                .after(structToMap(value.getStruct("after")))
                .source(structToMap(source))
                .connectorName(source.getString("name"))
                .build();
    }

    private String extractEntityId(Struct data) {
        Field primaryKey = data.schema().fields().stream()
                .filter(field -> "client_id".equals(field.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Primary key 'client_id' not found in CDC event data."));

        return String.valueOf(data.get(primaryKey));
    }

    private Map<String, Object> structToMap(Struct struct) {
        if (struct == null) {
            return Collections.emptyMap();
        }
        return struct.schema().fields().stream()
                .collect(Collectors.toMap(Field::name, field -> {
                    Object value = struct.get(field);
                    // Handle nested structs if necessary, though for this scope, we keep it simple
                    return value != null ? value.toString() : null;
                }));
    }
}
