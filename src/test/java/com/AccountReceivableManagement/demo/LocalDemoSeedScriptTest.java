package com.AccountReceivableManagement.demo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structure and SAFETY of the demo seed script. The script is never executed here (or anywhere by these tests): everything is
 * checked statically against its text and against the schema Hibernate generates for the real entities.
 */
class LocalDemoSeedScriptTest {

    /** Reference tables: seeded with INSERT IGNORE and allowed to hold rows already. Everything else must be empty. */
    static final Set<String> REFERENCE_TABLES = Set.of("currency_master", "payment_terms_master", "billing_type_master",
            "billing_frequency_master", "tax_regime_master", "tax_type_master", "tax_component_master", "tax_region_master");

    /** Tables that carry a plain project_id / billing_snapshot_id / client_id column WITHOUT a foreign key to the seeded rows. */
    static final Set<String> LOGICAL_REFERENCERS = Set.of("invoice", "receivable", "tax_calculation",
            "software_billing_history", "ar_project_tool_billing_config");

    private static String script;
    private static String code;
    private static String guard;
    private static List<SeedSql.Insert> inserts;

    @BeforeAll
    static void load() throws IOException {
        script = SeedSql.read();
        code = SeedSql.codeOnly(script);
        Matcher m = Pattern.compile("CREATE PROCEDURE ar_demo_seed_guard\\(\\)(.*?)END//", Pattern.DOTALL).matcher(script);
        assertThat(m.find()).as("guard procedure").isTrue();
        guard = m.group(1);
        inserts = SeedSql.inserts(script);
    }

    private static Set<String> insertedTables() {
        Set<String> tables = new LinkedHashSet<>();
        inserts.forEach(i -> tables.add(i.table()));
        return tables;
    }

    private static Set<String> quoted(String text, Pattern pattern) {
        Set<String> names = new TreeSet<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // No destructive or schema-changing statements
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    void containsNoDeleteUpdateTruncateOrSchemaChangingStatements() {
        Matcher forbidden = Pattern.compile("\\b(DELETE|UPDATE|TRUNCATE|ALTER|REPLACE\\s+INTO|DROP\\s+(TABLE|DATABASE|SCHEMA|USER|INDEX)|"
                + "CREATE\\s+(DATABASE|SCHEMA|TABLE|USER|INDEX)|GRANT|REVOKE|RENAME)\\b", Pattern.CASE_INSENSITIVE).matcher(code);

        boolean found = forbidden.find();
        assertThat(found).as(found ? "forbidden statement: " + forbidden.group() : "none").isFalse();
        // the only DDL is the guard procedure, created and dropped within the script
        assertThat(quoted(code, Pattern.compile("(?i)\\bCREATE\\s+(\\w+)"))).containsExactly("PROCEDURE");
        assertThat(quoted(code, Pattern.compile("(?i)\\bDROP\\s+(\\w+)"))).containsExactly("PROCEDURE");
    }

    @Test
    void everyStatementThatChangesDataIsAnInsert() {
        for (SeedSql.Insert insert : inserts) {
            assertThat(insert.rows()).as(insert.table()).isNotEmpty();
        }
        // every INSERT in the text was parsed
        long insertKeywords = Pattern.compile("(?i)\\bINSERT\\b").matcher(code).results().count();
        assertThat(insertKeywords).isEqualTo(inserts.size());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Ordering: guard -> one transaction -> inserts -> commit
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    void guardRunsBeforeAnythingChangesAndAllInsertsAreInOneTransaction() {
        int call = code.indexOf("CALL ar_demo_seed_guard()");
        int dropProc = code.indexOf("DROP PROCEDURE ar_demo_seed_guard;");
        int start = code.indexOf("START TRANSACTION;");
        int firstInsert = indexOfRegex(code, "(?i)\\bINSERT\\b");
        int lastInsert = lastIndexOfRegex(code, "(?i)\\bINSERT\\b");
        int commit = code.indexOf("COMMIT;");

        assertThat(call).isPositive();
        assertThat(code.indexOf("START TRANSACTION", start + 1)).as("exactly one START TRANSACTION").isEqualTo(-1);
        assertThat(code.indexOf("COMMIT;", commit + 1)).as("exactly one COMMIT").isEqualTo(-1);
        assertThat(call).isLessThan(dropProc);
        assertThat(dropProc).isLessThan(start);
        assertThat(start).isLessThan(firstInsert);
        assertThat(lastInsert).isLessThan(commit);
        assertThat(code.indexOf("INSERT")).as("no INSERT inside the guard or before the transaction").isGreaterThan(start);
        assertThat(code.substring(commit + "COMMIT;".length()).trim()).as("nothing runs after COMMIT").isEmpty();
        assertThat(code).doesNotContainIgnoringCase("autocommit").doesNotContainIgnoringCase("ROLLBACK");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The guard
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    void guardRefusesAnyDatabaseOtherThanArDemo() {
        assertThat(guard).contains("DECLARE DEMO_SCHEMA VARCHAR(64) DEFAULT 'ar_demo'");
        assertThat(guard).contains("DATABASE() IS NULL OR DATABASE() <> DEMO_SCHEMA");
    }

    @Test
    void aWrongDatabaseIsRefusedBeforeAnythingIsCreatedOrDropped() {
        String plain = SeedSql.withoutCommentLines(script); // literals kept, comment lines removed
        int refusal = plain.indexOf("SELECT CONCAT('Seed refused: current database is '");
        int failing = plain.indexOf("SELECT (SELECT 1 UNION ALL SELECT 2)");
        int firstDrop = indexOfRegex(plain, "(?i)\\bDROP\\b");
        int firstCreate = indexOfRegex(plain, "(?i)\\bCREATE\\b");

        assertThat(refusal).as("plain-SQL refusal message").isNotNegative();
        assertThat(failing).as("deliberate failure that stops the client").isGreaterThan(refusal);
        assertThat(failing).isLessThan(firstDrop).isLessThan(firstCreate);
        // both statements only fire outside ar_demo
        String early = plain.substring(refusal, firstDrop);
        assertThat(Pattern.compile("WHERE DATABASE\\(\\) IS NULL OR DATABASE\\(\\) <> 'ar_demo';").matcher(early).results().count())
                .isEqualTo(2);
        assertThat(early).doesNotContainIgnoringCase("INSERT").doesNotContainIgnoringCase("PROCEDURE");
    }

    @Test
    void headerAdmitsTheLeftoverGuardProcedureAfterALateRefusal() {
        String header = script.substring(0, script.indexOf("DROP PROCEDURE IF EXISTS ar_demo_seed_guard"));
        assertThat(header).contains("stays behind in ar_demo").contains("no DATA is changed");
        assertThat(header).doesNotContain("nothing is changed").doesNotContain("has no way to affect one");
    }

    @Test
    void guardSignalsWithMessagesThatFitMySqlsLimit() {
        assertThat(guard).contains("DECLARE v_msg VARCHAR(128)");
        long signals = Pattern.compile("SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;").matcher(guard).results().count();
        assertThat(signals).as("schema, missing tables, engine, first-run-only").isEqualTo(4);
        Matcher caps = Pattern.compile("SET v_msg = LEFT\\(.*?,\\s*(\\d+)\\);", Pattern.DOTALL).matcher(guard);
        int found = 0;
        while (caps.find()) {
            found++;
            assertThat(Integer.parseInt(caps.group(1))).isLessThanOrEqualTo(128);
        }
        assertThat(found).isEqualTo(4);
    }

    @Test
    void guardCheckExistenceAndInnoDbForEveryTableTheScriptInsertsInto() {
        Set<String> inUnion = quoted(guard.substring(0, guard.indexOf("IF v_missing")), Pattern.compile("(?:SELECT|UNION ALL SELECT) '(\\w+)'"));
        Set<String> inEngineList = quoted(guard.substring(guard.indexOf("INTO v_not_innodb"), guard.indexOf("IF v_not_innodb")),
                Pattern.compile("'(\\w+)'"));

        assertThat(inUnion).containsAll(insertedTables());
        assertThat(inEngineList).containsAll(insertedTables());
        assertThat(guard).contains("x.engine <> 'InnoDB'");
    }

    @Test
    void guardIsFirstRunOnlyAndRequiresEveryBusinessTableToBeEmpty() {
        String nonEmptyBlock = guard.substring(guard.indexOf("SET v_nonempty"), guard.indexOf("END", guard.indexOf("SET v_nonempty")));
        Set<String> emptyRequired = quoted(nonEmptyBlock, Pattern.compile("\\(SELECT COUNT\\(\\*\\) FROM (\\w+)\\) > 0"));
        Set<String> seededBusiness = new TreeSet<>(insertedTables());
        seededBusiness.removeAll(REFERENCE_TABLES);

        assertThat(emptyRequired).containsAll(seededBusiness);
        assertThat(nonEmptyBlock).contains("IF v_nonempty IS NOT NULL AND v_nonempty <> ''");
        assertThat(nonEmptyBlock).contains("SIGNAL SQLSTATE '45000'");
        // tells the developer what to do, before the error
        assertThat(nonEmptyBlock).contains("DROP DATABASE ar_demo").contains("first-run only");
    }

    @Test
    void guardCoversEveryTableThatReferencesTheSeededRowsAccordingToTheRealSchema() throws IOException {
        Set<String> seededBusiness = new TreeSet<>(insertedTables());
        seededBusiness.removeAll(REFERENCE_TABLES);
        Set<String> nonEmptyBlockTables = quoted(guard.substring(guard.indexOf("SET v_nonempty")),
                Pattern.compile("\\(SELECT COUNT\\(\\*\\) FROM (\\w+)\\) > 0"));

        Set<String> expected = new TreeSet<>(SeedSchema.get().tablesReferencing(seededBusiness)); // Hibernate's own FK graph
        expected.addAll(LOGICAL_REFERENCERS);
        expected.removeAll(REFERENCE_TABLES);

        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(nonEmptyBlockTables);
        assertThat(missing).as("tables that can hold rows referencing the seeded rows but are not required to be empty").isEmpty();
    }

    @Test
    void everyTableTheScriptTouchesUsesTheInnoDbEngineSoTheTransactionIsAtomic() throws IOException {
        SeedSchema schema = SeedSchema.get();
        for (String table : insertedTables()) {
            assertThat(schema.tables).as(table).containsKey(table);
            assertThat(schema.tables.get(table).engine()).as(table + " engine").isEqualToIgnoringCase("InnoDB");
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Honest documentation
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    void headerSaysFirstRunOnlyAndMakesNoRerunClaim() {
        String header = script.substring(0, script.indexOf("DROP PROCEDURE IF EXISTS ar_demo_seed_guard"));
        String lower = header.toLowerCase(Locale.ROOT);

        assertThat(header).contains("FIRST-RUN ONLY").contains("NOT RE-RUNNABLE").contains("DROP DATABASE ar_demo");
        assertThat(lower).doesNotContain("re-running is safe").doesNotContain("safe to re-run").doesNotContain("idempotent");
        assertThat(header).contains("ONE transaction").contains("rolls the whole transaction back");
        assertThat(header).contains("Do not use --force");
    }

    @Test
    void containsNoRealHostsAndOnlyReservedInvalidEmailDomains() {
        assertThat(script.toLowerCase(Locale.ROOT)).doesNotContain("aivencloud").doesNotContain("amazonaws").doesNotContain("rds.");
        Matcher emails = Pattern.compile("[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+)").matcher(script);
        while (emails.find()) {
            assertThat(emails.group(1)).as("e-mail domain in the seed").endsWith(".invalid");
        }
    }

    private static int indexOfRegex(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.start() : -1;
    }

    private static int lastIndexOfRegex(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        int last = -1;
        while (m.find()) {
            last = m.start();
        }
        return last;
    }
}
