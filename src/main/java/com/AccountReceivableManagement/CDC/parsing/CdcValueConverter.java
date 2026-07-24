package com.AccountReceivableManagement.CDC.parsing;

import com.AccountReceivableManagement.CDC.mapping.ColumnMapping;
import com.AccountReceivableManagement.CDC.mapping.FieldType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class CdcValueConverter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Convert a value from source to target type based on column mapping
     */
    public Object convertValue(Object value, ColumnMapping mapping) {
        if (value == null) {
            return null;
        }

        try {
            switch (mapping.getFieldType()) {
                case STRING:
                    return convertToString(value);
                case INTEGER:
                    return convertToInteger(value);
                case LONG:
                    return convertToLong(value);
                case DOUBLE:
                    return convertToDouble(value);
                case BIG_DECIMAL:
                    return convertToBigDecimal(value);
                case BOOLEAN:
                    return convertToBoolean(value);
                case UUID:
                    return convertToUuid(value);
                case LOCAL_DATE:
                    return convertToLocalDate(value);
                case LOCAL_DATE_TIME:
                    return convertToLocalDateTime(value);
                case ENUM:
                    return convertToEnum(value, mapping.getEnumClass());
                case JSON:
                    return value; // JSON handled as string
                default:
                    log.warn("Unknown field type: {}", mapping.getFieldType());
                    return value;
            }
        } catch (Exception e) {
            log.error("Failed to convert value for field: {}, value: {}, type: {}",
                    mapping.getTargetField(), value, mapping.getFieldType(), e);
            throw new RuntimeException("Value conversion failed", e);
        }
    }

    private String convertToString(Object value) {
        return value.toString();
    }

    private Integer convertToInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private Long convertToLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    private Double convertToDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private BigDecimal convertToBigDecimal(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof String) {
            return new BigDecimal((String) value);
        }
        throw new IllegalArgumentException("Cannot convert to BigDecimal: " + value);
    }

    private Boolean convertToBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }

        String val = value.toString();

        if ("1".equals(val)) {
            return true;
        }

        if ("0".equals(val)) {
            return false;
        }

        return Boolean.parseBoolean(val);
    }

    private UUID convertToUuid(Object value) {

        if (value == null) {
            return null;
        }

        if (value instanceof UUID) {
            return (UUID) value;
        }

        if (value instanceof byte[]) {
            ByteBuffer bb = ByteBuffer.wrap((byte[]) value);
            return new UUID(bb.getLong(), bb.getLong());
        }

        if (value instanceof String str) {

            // Standard UUID string
            if (str.contains("-")) {
                return UUID.fromString(str);
            }

            // Debezium Base64 encoded BINARY(16)
            byte[] bytes = Base64.getDecoder().decode(str);

            ByteBuffer bb = ByteBuffer.wrap(bytes);

            return new UUID(bb.getLong(), bb.getLong());
        }

        throw new IllegalArgumentException("Cannot convert UUID from: " + value);
    }

    private LocalDate convertToLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof String) {
            return LocalDate.parse((String) value, DATE_FORMATTER);
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        if (value instanceof Number) {
            long longValue = ((Number) value).longValue();

            // Debezium DATE is days since epoch
            if (longValue < 36500) { // Arbitrary threshold to distinguish from timestamps
                return LocalDate.ofEpochDay(longValue);
            } else {
                // Handle timestamps (ms or us)
                long millis;
                String longStr = String.valueOf(longValue);
                if (longStr.length() > 13) { // Microseconds
                    millis = TimeUnit.MICROSECONDS.toMillis(longValue);
                } else { // Milliseconds
                    millis = longValue;
                }
                return java.time.Instant.ofEpochMilli(millis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
            }
        }
        throw new IllegalArgumentException("Cannot convert to LocalDate: " + value);
    }

    private LocalDateTime convertToLocalDateTime(Object value) {

        if (value == null) {
            return null;
        }

        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }

        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }

        // Handle string representations, including ISO_OFFSET_DATE_TIME from Debezium
        if (value instanceof String str) {
            try {
                // Handles zoned timestamps (e.g., "2023-01-01T12:00:00Z")
                return java.time.ZonedDateTime.parse(str)
                        .withZoneSameInstant(ZoneId.systemDefault())
                        .toLocalDateTime();
            } catch (java.time.format.DateTimeParseException e) {
                // Fallback for non-zoned timestamps (e.g., "2023-01-01T12:00:00")
                return LocalDateTime.parse(str, DATE_TIME_FORMATTER);
            }
        }

        if (value instanceof Number number) {
            long longValue = number.longValue();

            // Debezium can send timestamps as microseconds (from DATETIME(6)) or milliseconds.
            // A 13-digit number is typically milliseconds since epoch.
            // A 16-digit number is typically microseconds since epoch.
            String longStr = String.valueOf(longValue);

            if (longStr.length() > 13) { // Assume microseconds
                long micros = longValue;
                long seconds = micros / 1_000_000;
                long nanos = (micros % 1_000_000) * 1000;
                return java.time.Instant.ofEpochSecond(seconds, nanos)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
            } else { // Assume milliseconds
                return java.time.Instant.ofEpochMilli(longValue)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
            }
        }

        throw new IllegalArgumentException("Cannot convert to LocalDateTime: " + value);
    }

    @SuppressWarnings("unchecked")
    private <T extends Enum<T>> T convertToEnum(Object value, Class<?> enumClass) {
        if (value == null || enumClass == null) {
            return null;
        }
        if (value instanceof Enum) {
            return (T) value;
        }
        String enumValue = value.toString().toUpperCase();
        return Enum.valueOf((Class<T>) enumClass, enumValue);
    }
}
