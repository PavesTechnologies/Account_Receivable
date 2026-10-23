package com.AccountReceivableManagement.demo;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Refuses to start the {@code local-demo} profile unless it is provably isolated from shared infrastructure.
 *
 * <p>It checks the <em>resolved</em> environment (so an OS variable such as {@code SPRING_DATASOURCE_URL}, or a value
 * loaded from {@code .env}, cannot silently win over the profile file). It only ever reports host names, flag names and
 * lengths - never URLs with credentials, passwords, secrets or tokens. When the profile is not active it does nothing, so
 * default, {@code local} and production behaviour is untouched.
 */
public final class LocalDemoIsolationValidator {

    public static final String PROFILE = "local-demo";

    /** Properties that must be explicitly {@code false}: every one of them defaults to enabled when absent. */
    static final List<String> MUST_BE_FALSE = List.of(
            "ar.cdc.infrastructure.enabled",
            "ar.cdc.inbox-processor.enabled",
            "ar.billing.status-schedulers.enabled",
            "client-cdc.enabled",
            "project-cdc.enabled",
            "project-cdc.milestone-initial-sync.enabled");

    /** Endpoints that must be blank or loopback (a local stub, a local SMTP catcher, no issuer). */
    static final List<String> BLANK_OR_LOOPBACK = List.of(
            "tms.api.base-url",
            "xms.api.base-url",
            "spring.mail.host",
            "spring.security.oauth2.resourceserver.jwt.issuer-uri",
            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            "client-cdc.hostname",
            "project-cdc.hostname",
            "client-cdc.jdbc-url",
            "project-cdc.jdbc-url");

    /** Shared-infrastructure environment variables whose host part must never appear in any guarded value. */
    static final List<String> SHARED_ENV_HOSTS = List.of("DB_HOST", "RMS_DB_HOST", "PMS_DB_HOST");
    static final List<String> SHARED_ENV_URLS = List.of("TMS_API_BASE_URL", "JWT_ISSUER_URI", "EXPENSE_MANAGEMENT_URL");

    /** Provider domains that are shared by definition. */
    static final List<String> SHARED_DOMAIN_SUFFIXES = List.of("aivencloud.com", "amazonaws.com");

    static final Set<String> FORBIDDEN_CO_ACTIVE_PROFILES =
            Set.of("prod", "production", "dev", "staging", "stage", "qa", "uat");

    static final int MIN_JWT_SECRET_LENGTH = 32;

    /**
     * Properties whose relaxed environment-variable name equals a shared variable the project already uses
     * ({@code tms.api.base-url} maps to {@code TMS_API_BASE_URL}). An OS variable outranks every config file, so the
     * profile file alone cannot protect them; the early guard pins them with a higher-precedence property source.
     * Must match application-local-demo.properties (checked by a test).
     */
    static final Map<String, Object> PINNED_PROPERTIES = Map.of("tms.api.base-url", "http://127.0.0.1:9");
    static final String PINNED_SOURCE_NAME = "localDemoPinnedProperties";

    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "::1", "[::1]", "0:0:0:0:0:0:0:1");
    private static final Pattern JDBC_AUTHORITY = Pattern.compile("^jdbc:mysql://([^/?#]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern JDBC_ANY_AUTHORITY = Pattern.compile("^jdbc:[a-zA-Z0-9]+://([^/?#]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_AUTHORITY = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://([^/?#]*)");

    private LocalDemoIsolationValidator() {
    }

    /** Throws {@link IllegalStateException} listing every violation; does nothing unless the profile is active. */
    public static void validateIfActive(Environment env) {
        if (!env.acceptsProfiles(Profiles.of(PROFILE))) {
            return;
        }
        List<String> violations = violations(env);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("local-demo isolation guard refused to start - shared infrastructure could be "
                    + "reached or a demo safeguard is missing:\n - " + String.join("\n - ", violations));
        }
    }

    /** All violations for an environment on which the profile is (assumed) active. */
    public static List<String> violations(Environment env) {
        List<String> violations = new ArrayList<>();

        for (String active : env.getActiveProfiles()) {
            if (FORBIDDEN_CO_ACTIVE_PROFILES.contains(active.toLowerCase(Locale.ROOT))) {
                violations.add("profile '" + active + "' must not be active together with " + PROFILE);
            }
        }

        List<String> sharedTokens = sharedHostTokens(env);

        // Datasource: must be a parsable jdbc:mysql URL whose host is loopback.
        String url = resolve(env, "spring.datasource.url", violations);
        if (url == null || url.isBlank()) {
            violations.add("spring.datasource.url is not set");
        } else {
            String host = hostOfJdbcUrl(url);
            if (host == null) {
                violations.add("spring.datasource.url is not a parsable jdbc:mysql URL");
            } else if (!isLoopback(host)) {
                violations.add("spring.datasource.url points to non-loopback host '" + host + "'");
            }
            checkSharedTokens("spring.datasource.url", url, sharedTokens, violations);
        }
        // The password only has to be resolvable (an unset AR_DEMO_DB_PASSWORD must stop the start); never read or print it.
        resolve(env, "spring.datasource.password", violations);

        for (String key : BLANK_OR_LOOPBACK) {
            String value = resolve(env, key, violations);
            if (value == null || value.isBlank()) {
                continue;
            }
            String host = hostOf(value);
            if (host != null && !host.isBlank() && !isLoopback(host)) {
                violations.add(key + " points to non-loopback host '" + host + "'");
            }
            checkSharedTokens(key, value, sharedTokens, violations);
        }

        for (String key : MUST_BE_FALSE) {
            String value = resolve(env, key, null);
            if (value == null || !"false".equalsIgnoreCase(value.trim())) {
                violations.add(key + " must be explicitly false under " + PROFILE + " (is: "
                        + (value == null || value.isBlank() ? "unset" : "'" + value.trim() + "'") + ")");
            }
        }
        String xms = resolve(env, "xms.api.enabled", null);
        if (xms != null && "true".equalsIgnoreCase(xms.trim())) {
            violations.add("xms.api.enabled must not be true under " + PROFILE);
        }

        String jwtSecret = resolve(env, "ar.demo.jwt.secret", null);
        if (jwtSecret == null || jwtSecret.length() < MIN_JWT_SECRET_LENGTH) {
            violations.add("ar.demo.jwt.secret (AR_DEMO_JWT_SECRET) must be set to at least " + MIN_JWT_SECRET_LENGTH
                    + " characters (currently " + (jwtSecret == null ? 0 : jwtSecret.length()) + ")");
        }
        return violations;
    }

    static boolean isLoopback(String host) {
        return host != null && LOOPBACK.contains(host.trim().toLowerCase(Locale.ROOT));
    }

    /** Host of a {@code jdbc:mysql://[user[:pw]@]host[:port]/db} URL; credentials are dropped and never returned. */
    static String hostOfJdbcUrl(String url) {
        Matcher m = JDBC_AUTHORITY.matcher(url.trim());
        return m.find() ? hostOfAuthority(m.group(1)) : null;
    }

    /** Host of a URL or, when the value is not a URL, the value itself (e.g. {@code spring.mail.host}). */
    static String hostOf(String value) {
        String v = value.trim();
        Matcher jdbc = JDBC_ANY_AUTHORITY.matcher(v);
        if (jdbc.find()) {
            return hostOfAuthority(jdbc.group(1));
        }
        Matcher m = URL_AUTHORITY.matcher(v);
        if (m.find()) {
            return hostOfAuthority(m.group(1));
        }
        if (v.contains("/") || v.contains("@")) {
            try {
                String host = URI.create(v).getHost();
                if (host != null) {
                    return host;
                }
            } catch (IllegalArgumentException ignored) {
                // fall through to treating the value as a bare host
            }
        }
        return hostOfAuthority(v);
    }

    private static String hostOfAuthority(String authority) {
        String a = authority;
        int at = a.lastIndexOf('@');
        if (at >= 0) {
            a = a.substring(at + 1); // drop user[:password]
        }
        if (a.startsWith("[")) {
            int end = a.indexOf(']');
            return end > 0 ? a.substring(0, end + 1) : a;
        }
        int colon = a.lastIndexOf(':');
        return colon >= 0 && a.indexOf(':') == colon ? a.substring(0, colon) : a;
    }

    private static List<String> sharedHostTokens(Environment env) {
        List<String> tokens = new ArrayList<>(SHARED_DOMAIN_SUFFIXES);
        for (String key : SHARED_ENV_HOSTS) {
            addToken(tokens, resolve(env, key, null));
        }
        for (String key : SHARED_ENV_URLS) {
            String v = resolve(env, key, null);
            addToken(tokens, v == null || v.isBlank() ? null : hostOf(v));
        }
        return tokens;
    }

    private static void addToken(List<String> tokens, String candidate) {
        if (candidate != null && !candidate.isBlank() && !isLoopback(candidate)) {
            tokens.add(candidate.trim().toLowerCase(Locale.ROOT));
        }
    }

    private static void checkSharedTokens(String key, String value, List<String> tokens, List<String> violations) {
        String lower = value.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (lower.contains(token)) {
                violations.add(key + " contains a shared-infrastructure host ('" + token + "')");
                return;
            }
        }
    }

    /** Resolves a property; an unresolvable placeholder is reported (key only) when {@code violations} is given. */
    private static String resolve(Environment env, String key, List<String> violations) {
        try {
            return env.getProperty(key);
        } catch (IllegalArgumentException unresolvable) {
            if (violations != null) {
                violations.add(key + " cannot be resolved (a required AR_DEMO_* variable is probably not set)");
            }
            return null;
        }
    }
}
