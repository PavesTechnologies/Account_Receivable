package com.AccountReceivableManagement.demo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates every value of the demo seed against the schema Hibernate generates for the REAL entities (columns, NOT NULL,
 * enums, lengths, decimals, dates, keys, foreign keys including natural-key subselects) and checks the data's own coherence.
 * Static: the seed is parsed, never executed.
 */
class LocalDemoSeedSchemaTest {

    private static SeedSchema schema;
    private static List<SeedSql.Insert> inserts;
    /** table -> column -> values seeded (hex for uuids, plain text/number otherwise) */
    private static final Map<String, Map<String, Set<String>>> seeded = new HashMap<>();

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    @BeforeAll
    static void load() throws IOException {
        schema = SeedSchema.get();
        inserts = SeedSql.inserts(SeedSql.read());
        for (SeedSql.Insert insert : inserts) {
            for (Map<String, String> row : insert.rows()) {
                row.forEach((column, token) -> {
                    String value = comparable(token);
                    if (value != null) {
                        seeded.computeIfAbsent(insert.table(), t -> new HashMap<>()).computeIfAbsent(column, c -> new HashSet<>()).add(value);
                    }
                });
            }
        }
    }

    /** uuid -> hex, string -> text, number -> as written; NULL and subselects -> null. */
    private static String comparable(String token) {
        if (SeedSql.isNull(token) || token.startsWith("(SELECT")) {
            return null;
        }
        if (token.startsWith("UNHEX(")) {
            return SeedSql.uuidHex(token);
        }
        if (token.startsWith("'")) {
            return SeedSql.str(token);
        }
        return token;
    }

    @Test
    void everyValueMatchesTheRealColumnDefinition() {
        List<String> problems = new ArrayList<>();
        for (SeedSql.Insert insert : inserts) {
            SeedSchema.TableDef table = schema.tables.get(insert.table());
            if (table == null) {
                problems.add("unknown table " + insert.table());
                continue;
            }
            for (String column : insert.columns()) {
                if (!table.columns().containsKey(column)) {
                    problems.add(insert.table() + ": unknown column " + column);
                }
            }
            for (SeedSchema.Column column : table.columns().values()) {
                if (column.notNull() && !insert.columns().contains(column.name())) {
                    problems.add(insert.table() + ": NOT NULL column " + column.name() + " is not supplied");
                }
            }
            for (Map<String, String> row : insert.rows()) {
                row.forEach((name, token) -> {
                    SeedSchema.Column column = table.columns().get(name);
                    if (column != null) {
                        checkValue(insert.table(), column, token, problems);
                    }
                });
            }
        }
        assertThat(problems).isEmpty();
    }

    private static void checkValue(String table, SeedSchema.Column column, String token, List<String> problems) {
        String where = table + "." + column.name();
        if (SeedSql.isNull(token)) {
            if (column.notNull()) {
                problems.add(where + ": NULL in a NOT NULL column");
            }
            return;
        }
        String type = column.type();
        try {
            if (type.startsWith("binary(16)")) {
                if (!token.startsWith("UNHEX(") && !token.startsWith("(SELECT")) {
                    problems.add(where + ": expected UNHEX(..) or a natural-key subselect");
                } else if (token.startsWith("UNHEX(")) {
                    SeedSql.uuidHex(token);
                }
            } else if (type.startsWith("bit")) {
                if (!token.equals("0") && !token.equals("1")) {
                    problems.add(where + ": bit must be 0 or 1");
                }
            } else if (type.startsWith("bigint") || type.startsWith("integer")) {
                Long.parseLong(token);
            } else if (column.decimal() != null) {
                BigDecimal value = new BigDecimal(token);
                int scale = Math.max(value.scale(), 0);
                int integerDigits = value.precision() - value.scale();
                if (scale > column.decimal()[1] || integerDigits > column.decimal()[0] - column.decimal()[1]) {
                    problems.add(where + ": " + token + " does not fit decimal(" + column.decimal()[0] + "," + column.decimal()[1] + ")");
                }
            } else if (type.equals("date") || type.startsWith("date ")) {
                LocalDate.parse(SeedSql.str(token));
            } else if (type.startsWith("datetime")) {
                LocalDateTime.parse(SeedSql.str(token), DATETIME);
            } else if (column.enumValues() != null) {
                String value = SeedSql.str(token);
                if (!column.enumValues().contains(value)) {
                    problems.add(where + ": '" + value + "' is not one of " + column.enumValues());
                }
            } else if (column.varcharLength() != null) {
                if (SeedSql.str(token).length() > column.varcharLength()) {
                    problems.add(where + ": longer than varchar(" + column.varcharLength() + ")");
                }
            } else if (!type.startsWith("TEXT")) {
                problems.add(where + ": unhandled column type " + type);
            }
        } catch (RuntimeException e) {
            problems.add(where + ": invalid value (" + e.getClass().getSimpleName() + ")");
        }
    }

    @Test
    void noPrimaryOrUniqueKeyIsDuplicatedWithinTheSeed() {
        List<String> problems = new ArrayList<>();
        for (SeedSql.Insert insert : inserts) {
            SeedSchema.TableDef table = schema.tables.get(insert.table());
            List<List<String>> keys = new ArrayList<>();
            keys.add(table.primaryKey());
            keys.addAll(schema.uniquesByTable.getOrDefault(insert.table(), List.of()));
            for (List<String> key : keys) {
                Set<List<String>> seen = new HashSet<>();
                for (Map<String, String> row : insert.rows()) {
                    List<String> values = new ArrayList<>();
                    for (String column : key) {
                        values.add(comparable(row.getOrDefault(column, "NULL")));
                    }
                    if (!values.contains(null) && !seen.add(values)) {
                        problems.add(insert.table() + ": duplicate " + key + " " + values);
                    }
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void everyForeignKeyPointsAtASeededRowOrAResolvableNaturalKey() {
        List<String> problems = new ArrayList<>();
        for (SeedSchema.ForeignKeyDef fk : schema.foreignKeys) {
            for (Map<String, String> row : SeedSql.rows(inserts, fk.table())) {
                String token = row.get(fk.column());
                if (token == null || SeedSql.isNull(token)) {
                    continue;
                }
                if (token.startsWith("(SELECT")) {
                    SeedSql.NaturalKey key = SeedSql.naturalKey(token);
                    if (!key.table().equals(fk.parent()) || !key.column().equals(fk.parentColumn())) {
                        problems.add(fk.table() + "." + fk.column() + ": subselect targets " + key.table() + "." + key.column());
                    } else if (!seeded.getOrDefault(fk.parent(), Map.of()).getOrDefault(key.naturalColumn(), Set.of()).contains(key.value())) {
                        problems.add(fk.table() + "." + fk.column() + ": natural key '" + key.value() + "' is not seeded in " + fk.parent());
                    }
                } else if (!seeded.getOrDefault(fk.parent(), Map.of()).getOrDefault(fk.parentColumn(), Set.of()).contains(comparable(token))) {
                    problems.add(fk.table() + "." + fk.column() + " -> " + fk.parent() + ": " + comparable(token) + " is not seeded");
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void insertIgnoreIsUsedOnlyForReferenceTables() {
        for (SeedSql.Insert insert : inserts) {
            assertThat(insert.ignore()).as(insert.table()).isEqualTo(LocalDemoSeedScriptTest.REFERENCE_TABLES.contains(insert.table()));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The data's own coherence
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    void snapshotsAreCoherentWithTheirItemsAndNothingDownstreamIsClaimed() {
        List<Map<String, String>> snapshots = SeedSql.rows(inserts, "billing_snapshot");
        List<Map<String, String>> items = SeedSql.rows(inserts, "billing_snapshot_item");
        assertThat(snapshots).hasSize(2);

        for (Map<String, String> snapshot : snapshots) {
            String hex = SeedSql.uuidHex(snapshot.get("billing_snapshot_id"));
            BigDecimal sum = BigDecimal.ZERO;
            for (Map<String, String> item : items) {
                if (SeedSql.uuidHex(item.get("billing_snapshot_id")).equals(hex)) {
                    BigDecimal amount = SeedSql.dec(item.get("amount"));
                    assertThat(SeedSql.dec(item.get("quantity")).multiply(SeedSql.dec(item.get("rate"))))
                            .as("item amount = quantity x rate").isEqualByComparingTo(amount);
                    sum = sum.add(amount);
                }
            }
            assertThat(SeedSql.dec(snapshot.get("subtotal"))).isEqualByComparingTo(sum);
            assertThat(SeedSql.dec(snapshot.get("total_amount"))).isEqualByComparingTo(sum);
            assertThat(SeedSql.dec(snapshot.get("expense_amount"))).isEqualByComparingTo("0.00");
            // nothing downstream is claimed: tax and the invoice come from the real API
            assertThat(SeedSql.str(snapshot.get("status"))).isEqualTo("READY_FOR_TAX");
            assertThat(SeedSql.rows(inserts, "tax_calculation")).isEmpty();
            assertThat(SeedSql.rows(inserts, "invoice")).isEmpty();
            assertThat(SeedSql.rows(inserts, "company_profile")).isEmpty();
        }
    }

    @Test
    void snapshotsCarryTheTaxRegionAndJurisdictionsTheWayRealSnapshotCreationDerivesThem() {
        Map<String, Map<String, String>> regionsByCode = new HashMap<>();
        SeedSql.rows(inserts, "tax_region_master").forEach(r -> regionsByCode.put(SeedSql.str(r.get("tax_region_code")), r));
        Map<Long, Map<String, String>> projects = new HashMap<>();
        SeedSql.rows(inserts, "project_master_reference").forEach(p -> projects.put(SeedSql.num(p.get("pms_project_id")), p));
        Map<String, Map<String, String>> configs = new HashMap<>();
        SeedSql.rows(inserts, "billing_configuration").forEach(c -> configs.put(SeedSql.uuidHex(c.get("billing_configuration_id")), c));

        for (Map<String, String> snapshot : SeedSql.rows(inserts, "billing_snapshot")) {
            Map<String, String> config = configs.get(SeedSql.uuidHex(snapshot.get("billing_configuration_id")));
            assertThat(config).as("snapshot's billing configuration is seeded").isNotNull();
            // tax_region_id and tax_region_code are copied from the configuration's tax region
            String regionCode = SeedSql.naturalKey(config.get("tax_region_id")).value();
            assertThat(SeedSql.naturalKey(snapshot.get("tax_region_id")).value()).isEqualTo(regionCode);
            assertThat(SeedSql.str(snapshot.get("tax_region_code"))).isEqualTo(regionCode);
            // source jurisdiction = the configuration's tax region code (upper case)
            assertThat(SeedSql.str(snapshot.get("source_tax_jurisdiction_code"))).isEqualTo(regionCode.toUpperCase());
            // destination = the code of the tax region whose NAME equals the project's primary_location
            String location = SeedSql.str(projects.get(SeedSql.num(snapshot.get("project_id"))).get("primary_location"));
            String destinationCode = regionsByCode.values().stream()
                    .filter(r -> SeedSql.str(r.get("tax_region_name")).equalsIgnoreCase(location))
                    .map(r -> SeedSql.str(r.get("tax_region_code")).toUpperCase()).findFirst().orElse(null);
            assertThat(destinationCode).as("a tax region is named like the project location '" + location + "'").isNotNull();
            assertThat(SeedSql.str(snapshot.get("destination_tax_jurisdiction_code"))).isEqualTo(destinationCode);
        }
    }

    @Test
    void everyBillingConfigurationHasATaxRegionWithAnApplicableTaxConfiguration() {
        List<Map<String, String>> taxConfigs = SeedSql.rows(inserts, "tax_configuration");
        for (Map<String, String> config : SeedSql.rows(inserts, "billing_configuration")) {
            String region = SeedSql.naturalKey(config.get("tax_region_id")).value();
            long matching = taxConfigs.stream().filter(t -> SeedSql.naturalKey(t.get("tax_region_id")).value().equals(region)
                    && SeedSql.bool(t.get("is_active"))).count();
            assertThat(matching).as("active tax configuration for region " + region).isEqualTo(1);
        }
    }

    @Test
    void taxConfigurationsObeyTheRulesTheTaxApiEnforces() {
        Map<String, Set<String>> typesByConfig = new HashMap<>();
        Set<String> activeTypes = new HashSet<>();
        SeedSql.rows(inserts, "tax_type_master").forEach(t -> {
            if (SeedSql.bool(t.get("is_active"))) {
                activeTypes.add(SeedSql.str(t.get("tax_type_code")));
            }
        });
        for (Map<String, String> component : SeedSql.rows(inserts, "tax_configuration_component")) {
            String typeCode = SeedSql.naturalKey(component.get("tax_type_id")).value();
            assertThat(activeTypes).as("tax type must be active").contains(typeCode);
            assertThat(SeedSql.dec(component.get("tax_rate"))).as("rate cannot be negative").isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(typesByConfig.computeIfAbsent(SeedSql.uuidHex(component.get("tax_configuration_id")), k -> new HashSet<>())
                    .add(typeCode)).as("a tax type is configured at most once per configuration").isTrue();
        }
        List<Map<String, String>> configs = SeedSql.rows(inserts, "tax_configuration");
        for (Map<String, String> config : configs) {
            assertThat(typesByConfig.get(SeedSql.uuidHex(config.get("tax_configuration_id")))).as("at least one component").isNotEmpty();
            LocalDate from = SeedSql.date(config.get("effective_from"));
            LocalDate to = SeedSql.date(config.get("effective_to"));
            assertThat(to == null || !to.isBefore(from)).as("effective end not before start").isTrue();
        }
        // no two active configurations of the same region overlap (here: one per region)
        Set<String> regions = new HashSet<>();
        for (Map<String, String> config : configs) {
            assertThat(regions.add(SeedSql.naturalKey(config.get("tax_region_id")).value())).as("one configuration per region").isTrue();
        }
        SeedSql.rows(inserts, "tax_region_master").forEach(r -> assertThat(SeedSql.bool(r.get("is_active"))).isTrue());
    }

    @Test
    void milestoneAndPaymentPlanFiguresAddUp() {
        BigDecimal billable = BigDecimal.ZERO;
        for (Map<String, String> milestone : SeedSql.rows(inserts, "project_milestone_reference")) {
            if (SeedSql.bool(milestone.get("billable"))) {
                billable = billable.add(SeedSql.dec(milestone.get("allocation_percentage")));
            }
            boolean ready = "READY_FOR_BILLING".equals(SeedSql.str(milestone.get("billing_readiness")));
            if (ready) {
                assertThat(SeedSql.str(milestone.get("execution_status"))).isEqualTo("COMPLETED");
                assertThat(SeedSql.bool(milestone.get("billable"))).isTrue();
            }
        }
        assertThat(billable).isEqualByComparingTo("100.00");

        BigDecimal percentage = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        for (Map<String, String> entry : SeedSql.rows(inserts, "billing_payment_entry")) {
            percentage = percentage.add(SeedSql.dec(entry.get("percentage")));
            amount = amount.add(SeedSql.dec(entry.get("amount")));
        }
        assertThat(percentage).isEqualByComparingTo("100.00");
        assertThat(amount).isEqualByComparingTo(SeedSql.dec(SeedSql.rows(inserts, "billing_milestone_plan").get(0).get("total_contract_value")));
    }

    @Test
    void projectStatusesIncludeThePmsValuesThatMotivatedTheStatusMigration() {
        Set<String> statuses = new HashSet<>();
        SeedSql.rows(inserts, "project_master_reference").forEach(p -> statuses.add(SeedSql.str(p.get("project_status"))));
        assertThat(statuses).contains("ACTIVE", "PLANNING", "ARCHIVED", "COMPLETED");
        assertThat(schema.tables.get("project_master_reference").columns().get("project_status").type()).startsWith("varchar(30)");
    }
}
