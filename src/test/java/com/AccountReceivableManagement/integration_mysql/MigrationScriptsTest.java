package com.AccountReceivableManagement.integration_mysql;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G-27 guard (no database needed): Flyway can only manage the migration
 * history if versions are unique and contiguous, and scripts from V12 on -
 * the ones Flyway will actually execute after an environment is baselined -
 * must be additive: they never remove or rewrite existing data.
 */
class MigrationScriptsTest {

    private static final Pattern NAME = Pattern.compile("V(\\d+)__[a-z0-9_]+\\.sql");

    /** First version Flyway executes on a database baselined at V11. */
    private static final int FIRST_MANAGED_VERSION = 12;

    private static final Pattern DESTRUCTIVE = Pattern.compile(
            "\\b(DROP\\s+(TABLE|COLUMN|INDEX)|DELETE\\s+FROM|TRUNCATE|UPDATE\\s+\\w+\\s+SET|MODIFY\\s+COLUMN|RENAME)\\b");

    private static TreeMap<Integer, Resource> scripts() throws IOException {
        TreeMap<Integer, Resource> byVersion = new TreeMap<>();
        for (Resource resource : new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/*.sql")) {
            Matcher name = NAME.matcher(resource.getFilename());
            assertThat(name.matches()).as("Flyway name: " + resource.getFilename()).isTrue();
            Resource previous = byVersion.put(Integer.parseInt(name.group(1)), resource);
            assertThat(previous).as("duplicate version " + name.group(1)).isNull();
        }
        return byVersion;
    }

    @Test
    void versionsAreUniqueAndContiguous() throws IOException {
        List<Integer> versions = new ArrayList<>(scripts().keySet());
        assertThat(versions).isNotEmpty();
        for (int i = 0; i < versions.size(); i++) {
            assertThat(versions.get(i)).isEqualTo(i + 1);
        }
    }

    @Test
    void flywayManagedScripts_areAdditive() throws IOException {
        for (var entry : scripts().tailMap(FIRST_MANAGED_VERSION).entrySet()) {
            String sql = new String(entry.getValue().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String statements = sql.lines().filter(line -> !line.trim().startsWith("--"))
                    .reduce("", (a, b) -> a + "\n" + b).toUpperCase(Locale.ROOT);
            assertThat(DESTRUCTIVE.matcher(statements).find())
                    .as("V" + entry.getKey() + " must be additive").isFalse();
        }
    }
}
