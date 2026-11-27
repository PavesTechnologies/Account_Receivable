package com.AccountReceivableManagement.cdc.parsing;

import com.AccountReceivableManagement.cdc.mapping.ColumnMapping;
import com.AccountReceivableManagement.cdc.mapping.FieldType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for the PMS -> AR project end_date +1 day CDC bug.
 * Debezium's Timestamp/MicroTimestamp logical types are UTC epoch values; reprojecting
 * them through the JVM's default (e.g. IST, UTC+5:30) timezone instead of UTC pushed a
 * late-in-the-day source value into the next calendar day.
 */
class CdcValueConverterDateTest {

    private final CdcValueConverter converter = new CdcValueConverter();
    private final ColumnMapping dateMapping = ColumnMapping.builder()
            .sourceColumn("end_date")
            .targetField("endDate")
            .fieldType(FieldType.LOCAL_DATE)
            .build();

    @Test
    void lateDayTimestampInIstContainerNoLongerRollsOverToNextDay() {
        TimeZone original = TimeZone.getDefault();
        try {
            // Reproduces the AR runtime environment where the bug was observed.
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));

            // PMS end_date = 2026-12-27 23:30:00 UTC, sent by Debezium as epoch millis
            // (e.g. source column is DATETIME rather than DATE).
            long epochMillisForDec27At2330Utc = LocalDateTime.of(2026, 12, 27, 23, 30, 0)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli();

            Object result = converter.convertValue(epochMillisForDec27At2330Utc, dateMapping);

            // Before the fix: .atZone(ZoneId.systemDefault()) added +5:30 -> 2026-12-28 05:00 -> wrong date.
            // After the fix: .atZone(ZoneOffset.UTC) keeps the source calendar day.
            assertEquals(LocalDate.of(2026, 12, 27), result);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void epochDayDateColumnIsUnaffectedByContainerTimeZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));

            // Debezium DATE columns arrive as days-since-epoch, independent of time zone.
            long epochDayForDec27 = LocalDate.of(2026, 12, 27).toEpochDay();

            Object result = converter.convertValue(epochDayForDec27, dateMapping);

            assertEquals(LocalDate.of(2026, 12, 27), result);
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
