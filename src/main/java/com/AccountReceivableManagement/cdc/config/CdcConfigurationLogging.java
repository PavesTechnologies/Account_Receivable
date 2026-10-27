package com.AccountReceivableManagement.cdc.config;

import org.slf4j.Logger;

import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Safe startup logging for the Debezium engine configuration.
 *
 * The Debezium properties contain secrets (the source database password and the offset-store / schema-history
 * credentials), so the configuration is NEVER logged as a whole. Only the keys on {@link #SAFE_KEYS} are looked up and
 * printed; every other key is invisible to the log, whatever it is called. There is deliberately no redaction step: nothing
 * outside the allowlist is ever read for logging, so a new secret property cannot leak by being added later.
 */
public final class CdcConfigurationLogging {

    /** Non-secret connector metadata: names, tables, modes and bookkeeping table names. Values here are never credentials. */
    static final List<String> SAFE_KEYS = List.of(
            "name",
            "connector.class",
            "topic.prefix",
            "database.include.list",
            "table.include.list",
            "snapshot.mode",
            "database.ssl.mode",
            "database.server.id",
            "offset.storage.jdbc.offset.table.name",
            "schema.history.internal.jdbc.schema.history.table.name");

    /** Printed instead of any JDBC URL that is not a plain, unambiguous scheme/host/port/database URL. */
    static final String URL_NOT_LOGGED = "(JDBC URL not logged: not a plain host/port/database URL)";
    static final String URL_NOT_CONFIGURED = "(not configured)";

    private static final int MAX_URL_LENGTH = 512;

    private static final String HOST = "(?:[A-Za-z0-9](?:[A-Za-z0-9.\\-]{0,251}[A-Za-z0-9])?|\\[[0-9A-Fa-f:.]{2,45}\\])";

    /**
     * jdbc:subprotocol[:subsubprotocol]://host[:port][/database] followed by nothing, or by a query ("?..."),
     * ";..." parameters or a fragment ("#...") that is matched only to be thrown away - it is never captured or printed.
     */
    private static final Pattern PLAIN_JDBC_URL = Pattern.compile(
            "^((?i:jdbc):[A-Za-z][A-Za-z0-9]*(?::[A-Za-z][A-Za-z0-9]*)?)://"
                    + "(" + HOST + ")"
                    + "(?::([0-9]{1,5}))?"
                    + "(?:/([A-Za-z0-9_.\\-]{0,128}))?"
                    + "(?:[?;#][\\x21-\\x7E]*)?$");

    private CdcConfigurationLogging() {
    }

    /** Logs the allowlisted metadata of {@code properties} under {@code title}, plus how many other settings exist. */
    public static void logSafeSummary(Logger log, String title, Properties properties) {
        log.info("========== {} ==========", title);
        int shown = 0;
        for (String key : SAFE_KEYS) {
            String value = properties.getProperty(key);
            if (value != null) {
                log.info("{} = {}", key, value);
                shown++;
            }
        }
        log.info("{} other setting(s) present but intentionally not logged (credentials and connection details)",
                Math.max(0, properties.size() - shown));
        log.info("====================================================");
    }

    /**
     * The only form of a JDBC URL that may be logged: {@code jdbc:<db>://<host>[:<port>][/<database>]}, rebuilt from the
     * validated pieces. Everything else yields the fixed placeholder {@link #URL_NOT_LOGGED}, never a partial URL.
     *
     * <ul>
     *   <li>Any '@' anywhere means user information might be present. A password may itself contain '?', ';', '/' or '#',
     *       so the URL cannot be split reliably; such a URL is refused outright (a plain JDBC URL has no use for '@').</li>
     *   <li>Whitespace, control or non-ASCII characters, an over-long URL, a missing host, an invalid port, a malformed
     *       database name, several hosts, or percent-encoding are all refused.</li>
     *   <li>A query string, ";key=value" parameters and a fragment are tolerated but discarded unread, so a password in
     *       them is never printed.</li>
     * </ul>
     */
    public static String safeJdbcTarget(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return URL_NOT_CONFIGURED;
        }
        String url = jdbcUrl.strip();
        if (url.length() > MAX_URL_LENGTH || url.indexOf('@') >= 0) {
            return URL_NOT_LOGGED;
        }
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c < 0x21 || c > 0x7E) { // whitespace, control characters and anything non-ASCII
                return URL_NOT_LOGGED;
            }
        }
        Matcher m = PLAIN_JDBC_URL.matcher(url);
        if (!m.matches()) {
            return URL_NOT_LOGGED;
        }
        String port = m.group(3);
        if (port != null && Integer.parseInt(port) > 65535) {
            return URL_NOT_LOGGED;
        }
        StringBuilder target = new StringBuilder(m.group(1)).append("://").append(m.group(2));
        if (port != null) {
            target.append(':').append(port);
        }
        if (m.group(4) != null && !m.group(4).isEmpty()) {
            target.append('/').append(m.group(4));
        }
        return target.toString();
    }
}
