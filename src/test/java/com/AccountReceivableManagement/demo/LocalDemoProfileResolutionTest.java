package com.AccountReceivableManagement.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resolves the REAL application.properties plus profile files exactly as Spring Boot does, against a simulated OS
 * environment (and .env) that is full of shared-infrastructure values - all fake, distinctive markers. Nothing here reads
 * the real .env, opens a connection or starts a context.
 */
class LocalDemoProfileResolutionTest {

    // Fake stand-ins for what a developer's shell / .env would hold. Distinctive so substring checks are reliable.
    static final String SHARED_AR_DB_HOST = "marker-ar-db.f.aivencloud.com";
    static final String SHARED_RMS_HOST = "marker-rms.abc.rds.amazonaws.com";
    static final String SHARED_PMS_HOST = "marker-pms.abc.rds.amazonaws.com";
    static final String SHARED_TMS_URL = "https://marker-tms.shared-dev.example.com/api";
    static final String SHARED_UMS_ISSUER = "https://marker-ums.shared-dev.example.com/realms/paves";
    static final String SHARED_XMS_URL = "https://marker-xms.shared-dev.example.com";

    static final String DEMO_DB_PASSWORD = "demo-local-db-password";

    /** Everything an developer machine could have, shared values included, plus the two AR_DEMO_* variables. */
    static Map<String, Object> osEnvironment() {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("DB_HOST", SHARED_AR_DB_HOST);
        env.put("DB_PORT", "12345");
        env.put("DB_NAME", "marker_marker_ar_db");
        env.put("DB_USERNAME", "marker-shared-db-user");
        env.put("DB_PASSWORD", "marker-shared-db-password");
        env.put("RMS_DB_HOST", SHARED_RMS_HOST);
        env.put("RMS_DB_PORT", "3306");
        env.put("RMS_DB_NAME", "marker_rms");
        env.put("RMS_DB_USERNAME", "marker-rms-user");
        env.put("RMS_DB_PASSWORD", "marker-rms-password");
        env.put("PMS_DB_HOST", SHARED_PMS_HOST);
        env.put("PMS_DB_PORT", "3306");
        env.put("PMS_DB_NAME", "marker_pms");
        env.put("PMS_DB_USERNAME", "marker-pms-user");
        env.put("PMS_DB_PASSWORD", "marker-pms-password");
        env.put("TMS_API_BASE_URL", SHARED_TMS_URL);
        env.put("EXPENSE_MANAGEMENT_URL", SHARED_XMS_URL);
        env.put("XMS_ENABLED", "true");
        env.put("MAIL_USERNAME", "marker-mail-user@example.com");
        env.put("MAIL_PASSWORD", "marker-mail-password");
        env.put("JWT_ISSUER_URI", SHARED_UMS_ISSUER);
        env.put("AUTH_JWT_ALGORITHM", "HS256");
        env.put("AUTH_JWT_SECRET", "marker-shared-ums-signing-secret");
        env.put("AUTH_JWT_ISSUER", "marker-shared-issuer");
        env.put("AUTH_JWT_AUDIENCE", "marker-shared-audience");
        env.put("AUTH_JWT_ROLES_CLAIM", "marker-roles-claim");
        env.put("AUTH_JWT_REQUIRED_ROLE", "MARKER_ROLE");
        env.put("AR_DEMO_DB_PASSWORD", DEMO_DB_PASSWORD);
        env.put("AR_DEMO_JWT_SECRET", LocalDemoIsolationValidatorTest.SECRET);
        return env;
    }

    /** Values that identify shared infrastructure; none of them may appear in anything the demo profile resolves. */
    static List<String> sharedMarkers(Map<String, Object> os) {
        List<String> markers = new ArrayList<>();
        os.forEach((k, v) -> {
            if (!k.startsWith("AR_DEMO_") && !k.endsWith("_PORT") && !"XMS_ENABLED".equals(k)
                    && !"AUTH_JWT_ALGORITHM".equals(k)) {
                markers.add(String.valueOf(v));
            }
        });
        return markers;
    }

    static StandardEnvironment resolve(Map<String, Object> osEnv, String... profiles) {
        StandardEnvironment env = new StandardEnvironment();
        // Behave like Spring Boot's systemEnvironment source (relaxed names such as SPRING_DATASOURCE_URL), not the real OS.
        env.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, osEnv));
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        if (profiles.length > 0) {
            env.setActiveProfiles(profiles);
        }
        ConfigDataEnvironmentPostProcessor.applyTo(env);
        return env;
    }

    /** Config files + profiles, then the early guard (pinning and validation) exactly as spring.factories runs it at start. */
    static StandardEnvironment startLikeBoot(Map<String, Object> osEnv, String... profiles) {
        StandardEnvironment env = resolve(osEnv, profiles);
        new LocalDemoIsolationEnvironmentPostProcessor().postProcessEnvironment(env, null);
        return env;
    }

    /** Every property that comes from the application's own config files, resolved. Unresolvable ones are skipped. */
    static Map<String, String> resolvedFromConfigFiles(StandardEnvironment env) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (PropertySource<?> source : env.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable && source.getName().startsWith("Config resource")) {
                for (String name : enumerable.getPropertyNames()) {
                    try {
                        String value = env.getProperty(name);
                        if (value != null) {
                            resolved.put(name, value);
                        }
                    } catch (IllegalArgumentException unresolvablePlaceholder) {
                        // nothing was resolved to a value, so nothing can leak
                    }
                }
            }
        }
        return resolved;
    }

    @Test
    void demoProfileResolvesToLocalValuesEvenWhenSharedVariablesArePresent() {
        StandardEnvironment env = startLikeBoot(osEnvironment(), "local-demo");

        assertThat(env.getProperty("spring.datasource.url")).startsWith("jdbc:mysql://127.0.0.1:3399/ar_demo");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("ar_demo_app");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo(DEMO_DB_PASSWORD);
        assertThat(env.getProperty("tms.api.base-url")).isEqualTo("http://127.0.0.1:9");
        assertThat(env.getProperty("xms.api.enabled")).isEqualTo("false");
        assertThat(env.getProperty("xms.api.base-url")).isEmpty();
        assertThat(env.getProperty("spring.mail.host")).isEqualTo("127.0.0.1");
        assertThat(env.getProperty("spring.mail.username")).isEqualTo("demo@ar-demo.invalid");
        assertThat(env.getProperty("spring.mail.password")).isEmpty();
        assertThat(env.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri")).isEmpty();
        assertThat(env.getProperty("app.security.jwt.secret")).isEqualTo(LocalDemoIsolationValidatorTest.SECRET);
        assertThat(env.getProperty("app.security.jwt.issuer")).isEqualTo(DemoJwtConfig.ISSUER);
        assertThat(env.getProperty("app.security.jwt.required-role")).isEqualTo("FINANCE_EXECUTIVE");
        for (String cdc : List.of("client-cdc", "project-cdc")) {
            for (String field : List.of("hostname", "port", "database-name", "username", "password", "jdbc-url")) {
                assertThat(env.getProperty(cdc + "." + field)).as(cdc + "." + field).isEmpty();
            }
        }
    }

    @Test
    void nothingResolvedFromTheConfigFilesContainsAnySharedValue() {
        Map<String, Object> os = osEnvironment();
        Map<String, String> resolved = resolvedFromConfigFiles(startLikeBoot(os, "local-demo"));

        assertThat(resolved).isNotEmpty();
        List<String> leaks = new ArrayList<>();
        resolved.forEach((name, value) -> sharedMarkers(os).stream()
                .filter(value::contains)
                .forEach(marker -> leaks.add(name)));
        // Names only: values (which could be secrets) are never put into the failure message.
        assertThat(leaks).as("properties that resolve to a shared value under local-demo").isEmpty();
    }

    @Test
    void demoStartsWithOnlyTheDemoVariablesDefinedAndNeedsNoSharedVariable() throws IOException {
        Map<String, Object> onlyDemo = new LinkedHashMap<>();
        onlyDemo.put("AR_DEMO_DB_PASSWORD", DEMO_DB_PASSWORD);
        onlyDemo.put("AR_DEMO_JWT_SECRET", LocalDemoIsolationValidatorTest.SECRET);
        StandardEnvironment env = startLikeBoot(onlyDemo, "local-demo");

        // 1) every property in the config files resolves (no ${DB_HOST}-style placeholder is left needing a shared variable)
        List<String> unresolvable = new ArrayList<>();
        for (PropertySource<?> source : env.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable && source.getName().startsWith("Config resource")) {
                for (String name : enumerable.getPropertyNames()) {
                    try {
                        env.getProperty(name);
                    } catch (IllegalArgumentException e) {
                        unresolvable.add(name);
                    }
                }
            }
        }
        assertThat(unresolvable).as("properties that cannot resolve without a shared variable").isEmpty();

        // 2) every @Value key without a default used by the application code resolves too
        java.util.regex.Pattern valueKey = java.util.regex.Pattern.compile("@Value\\(\"\\$\\{([^:}]+)\\}\"\\)");
        List<String> missing = new ArrayList<>();
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java"))) {
            for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                java.util.regex.Matcher m = valueKey.matcher(java.nio.file.Files.readString(file));
                while (m.find()) {
                    try {
                        if (env.getProperty(m.group(1)) == null) {
                            missing.add(m.group(1));
                        }
                    } catch (IllegalArgumentException e) {
                        missing.add(m.group(1));
                    }
                }
            }
        }
        assertThat(missing).as("@Value keys that do not resolve under local-demo").isEmpty();
    }

    @Test
    void allGatedComponentsAreExplicitlyDisabledByTheProfile() {
        StandardEnvironment env = startLikeBoot(osEnvironment(), "local-demo");

        for (String flag : LocalDemoIsolationValidator.MUST_BE_FALSE) {
            assertThat(env.getProperty(flag)).as(flag).isEqualTo("false");
        }
    }

    @Test
    void guardAcceptsTheRealProfileEvenThoughSharedVariablesArePresent() {
        StandardEnvironment env = startLikeBoot(osEnvironment(), "local-demo"); // throws if the guard refuses
        assertThat(LocalDemoIsolationValidator.violations(env)).isEmpty();
    }

    @Test
    void osVariableNamedLikeARelaxedPropertyWouldOutrankTheProfileFileSoTheEarlyGuardPinsIt() {
        // Without the guard the shared TMS URL wins: tms.api.base-url is the relaxed form of TMS_API_BASE_URL.
        assertThat(resolve(osEnvironment(), "local-demo").getProperty("tms.api.base-url")).isEqualTo(SHARED_TMS_URL);
        // With the early guard (what spring.factories runs at start) it is pinned to the local value.
        assertThat(startLikeBoot(osEnvironment(), "local-demo").getProperty("tms.api.base-url")).isEqualTo("http://127.0.0.1:9");
        // Other profiles are not pinned.
        assertThat(startLikeBoot(osEnvironment(), "local").getProperty("tms.api.base-url")).isEqualTo(SHARED_TMS_URL);
        assertThat(startLikeBoot(osEnvironment()).getProperty("tms.api.base-url")).isEqualTo(SHARED_TMS_URL);
    }

    @Test
    void everyPropertyShadowedByASharedVariableNameIsPinnedAndInSyncWithTheProfileFile() throws IOException {
        // A property is shadowable when its relaxed env name equals an env variable that application.properties itself
        // reads through a ${...} placeholder (so a developer's shell / .env will normally define it).
        String base = read("application.properties");
        java.util.Set<String> sharedVariables = new java.util.HashSet<>();
        java.util.regex.Matcher placeholder = java.util.regex.Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)[:}]").matcher(base);
        while (placeholder.find()) {
            sharedVariables.add(placeholder.group(1));
        }
        java.util.Properties demo = new java.util.Properties();
        try (InputStream in = LocalDemoProfileResolutionTest.class.getClassLoader()
                .getResourceAsStream("application-local-demo.properties")) {
            demo.load(in);
        }
        java.util.Set<String> keys = new java.util.TreeSet<>(demo.stringPropertyNames());
        for (String line : base.split("\\R")) {
            java.util.regex.Matcher key = java.util.regex.Pattern.compile("^([A-Za-z0-9_.\\-]+)\\s*=").matcher(line);
            if (key.find()) {
                keys.add(key.group(1));
            }
        }

        assertThat(sharedVariables).contains("TMS_API_BASE_URL", "DB_HOST", "JWT_ISSUER_URI");
        for (String key : keys) {
            String relaxed = key.replaceAll("[.\\-]", "_").toUpperCase(java.util.Locale.ROOT);
            if (sharedVariables.contains(relaxed)) {
                assertThat(LocalDemoIsolationValidator.PINNED_PROPERTIES)
                        .as(key + " is shadowed by the shared variable " + relaxed).containsKey(key);
                assertThat(demo.getProperty(key)).as(key + " in the profile file")
                        .isEqualTo(LocalDemoIsolationValidator.PINNED_PROPERTIES.get(key));
            }
        }
    }

    @Test
    void guardRejectsAnOperatingSystemVariableThatWouldOverrideTheProfile() {
        for (Map.Entry<String, String> override : Map.of(
                "SPRING_DATASOURCE_URL", "jdbc:mysql://" + SHARED_AR_DB_HOST + ":12345/marker_ar_db",
                "SPRING_MAIL_HOST", "smtp.gmail.com",
                "CLIENT_CDC_ENABLED", "true",
                "PROJECT_CDC_ENABLED", "true",
                "AR_CDC_INBOX_PROCESSOR_ENABLED", "true",
                "AR_BILLING_STATUS_SCHEDULERS_ENABLED", "true",
                "PROJECT_CDC_MILESTONE_INITIAL_SYNC_ENABLED", "true").entrySet()) {
            Map<String, Object> os = osEnvironment();
            os.put(override.getKey(), override.getValue());

            assertThat(LocalDemoIsolationValidator.violations(resolve(os, "local-demo")))
                    .as(override.getKey()).isNotEmpty();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> startLikeBoot(os, "local-demo"))
                    .as(override.getKey() + " must stop the start").isInstanceOf(IllegalStateException.class);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Default / local / production behaviour is preserved
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    void defaultProfileStillResolvesTheSharedValuesAndHasNoDemoSettings() {
        StandardEnvironment env = resolve(osEnvironment());

        assertThat(env.getProperty("spring.datasource.url")).contains(SHARED_AR_DB_HOST);
        assertThat(env.getProperty("client-cdc.hostname")).isEqualTo(SHARED_RMS_HOST);
        assertThat(env.getProperty("project-cdc.hostname")).isEqualTo(SHARED_PMS_HOST);
        assertThat(env.getProperty("tms.api.base-url")).isEqualTo(SHARED_TMS_URL);
        assertThat(env.getProperty("client-cdc.enabled")).isEqualTo("true");
        assertThat(env.getProperty("project-cdc.enabled")).isEqualTo("true");
        assertThat(env.getProperty("project-cdc.milestone-initial-sync.enabled")).isEqualTo("true");
        // The new gates are not set anywhere by default, so they keep their default of "enabled".
        for (String gate : List.of("ar.cdc.infrastructure.enabled", "ar.cdc.inbox-processor.enabled",
                "ar.billing.status-schedulers.enabled")) {
            assertThat(env.getProperty(gate)).as(gate).isNull();
        }
        // The guard is inert without the profile.
        LocalDemoIsolationValidator.validateIfActive(env);
    }

    @Test
    void localProfileIsStillNotIsolatedAndIsUnchanged() {
        StandardEnvironment env = resolve(osEnvironment(), "local");

        assertThat(env.getProperty("spring.datasource.url")).contains(SHARED_AR_DB_HOST);
        assertThat(env.getProperty("client-cdc.enabled")).isEqualTo("true");
        assertThat(env.getProperty("ar.expense-fx.provider")).isEqualTo("database");
        assertThat(env.getProperty("ar.cdc.inbox-processor.enabled")).isNull();
        LocalDemoIsolationValidator.validateIfActive(env);
    }

    @Test
    void existingProfileFilesContainNoDemoSettings() throws IOException {
        for (String file : List.of("application.properties", "application-local.properties")) {
            String content = read(file);
            for (String demoKey : List.of("ar.cdc.", "ar.billing.status-schedulers", "ar.demo.", "AR_DEMO_", "local-demo")) {
                assertThat(content).as(file + " must not mention " + demoKey).doesNotContain(demoKey);
            }
        }
    }

    private static String read(String classpathFile) throws IOException {
        try (InputStream in = LocalDemoProfileResolutionTest.class.getClassLoader().getResourceAsStream(classpathFile)) {
            assertThat(in).as(classpathFile).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
