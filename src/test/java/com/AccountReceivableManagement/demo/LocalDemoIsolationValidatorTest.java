package com.AccountReceivableManagement.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rejection and acceptance cases of the local-demo isolation guard. Pure unit test: no context, no I/O, no network. */
class LocalDemoIsolationValidatorTest {

    static final String SECRET = "0123456789abcdef0123456789abcdef-demo-only";

    /** A fully isolated environment, as the local-demo profile file produces it. */
    static Map<String, Object> isolated() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("spring.datasource.url", "jdbc:mysql://127.0.0.1:3399/ar_demo?sslMode=PREFERRED");
        p.put("spring.datasource.password", "local-demo-db-password");
        p.put("tms.api.base-url", "http://127.0.0.1:9");
        p.put("xms.api.enabled", "false");
        p.put("xms.api.base-url", "");
        p.put("spring.mail.host", "127.0.0.1");
        p.put("spring.security.oauth2.resourceserver.jwt.issuer-uri", "");
        p.put("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", "");
        p.put("client-cdc.hostname", "");
        p.put("project-cdc.hostname", "");
        p.put("client-cdc.jdbc-url", "");
        p.put("project-cdc.jdbc-url", "");
        for (String flag : LocalDemoIsolationValidator.MUST_BE_FALSE) {
            p.put(flag, "false");
        }
        p.put("ar.demo.jwt.secret", SECRET);
        return p;
    }

    /** Hermetic environment (no real OS environment or system properties) with the given sources and profiles. */
    static StandardEnvironment env(Map<String, Object> properties, String... profiles) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.getPropertySources().addFirst(new MapPropertySource("test", properties));
        env.setActiveProfiles(profiles);
        return env;
    }

    static StandardEnvironment demoEnv(Map<String, Object> properties) {
        return env(properties, LocalDemoIsolationValidator.PROFILE);
    }

    private static Map<String, Object> with(String key, Object value) {
        Map<String, Object> p = isolated();
        p.put(key, value);
        return p;
    }

    @Test
    void fullyIsolatedEnvironmentIsAccepted() {
        assertThat(LocalDemoIsolationValidator.violations(demoEnv(isolated()))).isEmpty();
        assertThatCode(() -> LocalDemoIsolationValidator.validateIfActive(demoEnv(isolated()))).doesNotThrowAnyException();
    }

    @Test
    void doesNothingWhenTheProfileIsNotActive() {
        Map<String, Object> shared = new LinkedHashMap<>();
        shared.put("spring.datasource.url", "jdbc:mysql://shared-db.f.aivencloud.com:12345/marker_ar_db");
        shared.put("client-cdc.enabled", "true");

        assertThatCode(() -> LocalDemoIsolationValidator.validateIfActive(env(shared))).doesNotThrowAnyException();
        assertThatCode(() -> LocalDemoIsolationValidator.validateIfActive(env(shared, "local"))).doesNotThrowAnyException();
        assertThatCode(() -> LocalDemoIsolationValidator.validateIfActive(env(shared, "prod"))).doesNotThrowAnyException();
    }

    @Test
    void rejectsASharedAivenDatasourceAndNeverPrintsCredentials() {
        Map<String, Object> p = with("spring.datasource.url",
                "jdbc:mysql://appuser:topsecretpw@acct-db.f.aivencloud.com:12345/marker_ar_db?sslMode=REQUIRED");

        assertThatThrownBy(() -> LocalDemoIsolationValidator.validateIfActive(demoEnv(p)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-loopback host 'acct-db.f.aivencloud.com'")
                .hasMessageContaining("shared-infrastructure host ('aivencloud.com')")
                .hasMessageNotContaining("topsecretpw")
                .hasMessageNotContaining("appuser");
    }

    @Test
    void rejectsSharedRdsAndNonLoopbackHostsForEveryGuardedEndpoint() {
        for (String key : LocalDemoIsolationValidator.BLANK_OR_LOOPBACK) {
            boolean jdbc = key.endsWith("jdbc-url");
            boolean bareHost = key.endsWith("hostname") || key.equals("spring.mail.host");
            String shared = jdbc ? "jdbc:mysql://marker-rds-instance.abc.ap-south-1.rds.amazonaws.com:3306/pms"
                    : bareHost ? "marker-rds-instance.abc.ap-south-1.rds.amazonaws.com"
                    : "https://shared.example-corp.net/path";
            List<String> violations = LocalDemoIsolationValidator.violations(demoEnv(with(key, shared)));

            assertThat(violations).as(key).anyMatch(v -> v.startsWith(key + " points to non-loopback host"));
        }
    }

    @Test
    void rejectsRealSmtpHost() {
        assertThat(LocalDemoIsolationValidator.violations(demoEnv(with("spring.mail.host", "smtp.gmail.com"))))
                .anyMatch(v -> v.contains("spring.mail.host points to non-loopback host 'smtp.gmail.com'"));
    }

    @ParameterizedTest
    @MethodSource("mustBeFalseFlags")
    void rejectsEveryFlagThatIsTrueOrUnset(String flag) {
        Map<String, Object> enabled = with(flag, "true");
        assertThat(LocalDemoIsolationValidator.violations(demoEnv(enabled)))
                .anyMatch(v -> v.startsWith(flag + " must be explicitly false") && v.contains("'true'"));

        Map<String, Object> unset = isolated();
        unset.remove(flag); // absent means "enabled" for every gated component, so it must be refused
        assertThat(LocalDemoIsolationValidator.violations(demoEnv(unset)))
                .anyMatch(v -> v.startsWith(flag + " must be explicitly false") && v.contains("unset"));
    }

    static List<String> mustBeFalseFlags() {
        return LocalDemoIsolationValidator.MUST_BE_FALSE;
    }

    @Test
    void rejectsXmsEnabled() {
        assertThat(LocalDemoIsolationValidator.violations(demoEnv(with("xms.api.enabled", "true"))))
                .anyMatch(v -> v.contains("xms.api.enabled must not be true"));
    }

    @Test
    void rejectsMissingOrShortJwtSecretWithoutPrintingIt() {
        Map<String, Object> missing = isolated();
        missing.remove("ar.demo.jwt.secret");
        assertThat(LocalDemoIsolationValidator.violations(demoEnv(missing)))
                .anyMatch(v -> v.contains("ar.demo.jwt.secret") && v.contains("currently 0"));

        Map<String, Object> shortSecret = with("ar.demo.jwt.secret", "short-secret-value");
        List<String> violations = LocalDemoIsolationValidator.violations(demoEnv(shortSecret));
        assertThat(violations).anyMatch(v -> v.contains("currently 18"));
        assertThat(String.join("|", violations)).doesNotContain("short-secret-value");
    }

    @Test
    void rejectsEnvironmentProfilesActiveTogetherWithLocalDemo() {
        for (String other : List.of("prod", "production", "dev", "staging", "uat", "qa")) {
            assertThat(LocalDemoIsolationValidator.violations(env(isolated(), "local-demo", other)))
                    .as(other).anyMatch(v -> v.contains("profile '" + other + "' must not be active"));
        }
        // the existing "local" profile only sets expense-FX values and may be combined
        assertThat(LocalDemoIsolationValidator.violations(env(isolated(), "local-demo", "local"))).isEmpty();
    }

    @Test
    void sharedEnvironmentVariablesPresentButUnusedDoNotBreakAnIsolatedProfile() {
        Map<String, Object> p = isolated();
        p.put("DB_HOST", "acct-db.f.aivencloud.com");
        p.put("RMS_DB_HOST", "rms-dev.abc.rds.amazonaws.com");
        p.put("PMS_DB_HOST", "pms-dev.abc.rds.amazonaws.com");
        p.put("TMS_API_BASE_URL", "https://tms.shared-dev.example.com/api");
        p.put("JWT_ISSUER_URI", "https://ums.shared-dev.example.com");

        assertThat(LocalDemoIsolationValidator.violations(demoEnv(p))).isEmpty();
    }

    @Test
    void rejectsAGuardedValueThatEmbedsAHostTakenFromTheSharedEnvironment() {
        Map<String, Object> p = with("tms.api.base-url", "http://127.0.0.1:9/?via=custom-shared-host.internal");
        p.put("TMS_API_BASE_URL", "https://custom-shared-host.internal/api");

        assertThat(LocalDemoIsolationValidator.violations(demoEnv(p)))
                .anyMatch(v -> v.contains("tms.api.base-url contains a shared-infrastructure host ('custom-shared-host.internal')"));
    }

    @Test
    void unresolvablePlaceholderIsReportedByKeyOnly() {
        Map<String, Object> p = with("spring.datasource.password", "${AR_DEMO_DB_PASSWORD}");

        assertThat(LocalDemoIsolationValidator.violations(demoEnv(p)))
                .anyMatch(v -> v.startsWith("spring.datasource.password cannot be resolved")
                        && v.contains("AR_DEMO_*") && !v.contains("local-demo-db-password"));
    }

    @Test
    void missingOrUnparsableDatasourceUrlIsRejected() {
        Map<String, Object> missing = isolated();
        missing.remove("spring.datasource.url");
        assertThat(LocalDemoIsolationValidator.violations(demoEnv(missing)))
                .anyMatch(v -> v.contains("spring.datasource.url is not set"));
        assertThat(LocalDemoIsolationValidator.violations(demoEnv(with("spring.datasource.url", "postgresql://localhost/x"))))
                .anyMatch(v -> v.contains("not a parsable jdbc:mysql URL"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "jdbc:mysql://127.0.0.1:3399/ar_demo?x=1|127.0.0.1",
            "jdbc:mysql://localhost/ar_demo|localhost",
            "jdbc:mysql://user:pw@db.example.com:3306/x|db.example.com",
            "jdbc:mysql://[::1]:3306/x|[::1]",
            "JDBC:MYSQL://LocalHost:3306/x|LocalHost"})
    void parsesJdbcHostsAndDropsCredentials(String url, String host) {
        assertThat(LocalDemoIsolationValidator.hostOfJdbcUrl(url)).isEqualTo(host);
        assertThat(LocalDemoIsolationValidator.hostOf(url)).isEqualTo(host);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "http://127.0.0.1:9|127.0.0.1",
            "https://tms.example.com/api/v1|tms.example.com",
            "smtp.gmail.com|smtp.gmail.com",
            "127.0.0.1|127.0.0.1",
            "https://user:pw@host.example.com:8443/p|host.example.com"})
    void parsesUrlAndBareHosts(String value, String host) {
        assertThat(LocalDemoIsolationValidator.hostOf(value)).isEqualTo(host);
    }

    @Test
    void loopbackRecognition() {
        assertThat(LocalDemoIsolationValidator.isLoopback("localhost")).isTrue();
        assertThat(LocalDemoIsolationValidator.isLoopback("127.0.0.1")).isTrue();
        assertThat(LocalDemoIsolationValidator.isLoopback("::1")).isTrue();
        assertThat(LocalDemoIsolationValidator.isLoopback("localhost.attacker.example")).isFalse();
        assertThat(LocalDemoIsolationValidator.isLoopback("127.0.0.1.attacker.example")).isFalse();
        assertThat(LocalDemoIsolationValidator.isLoopback("db.f.aivencloud.com")).isFalse();
    }
}
