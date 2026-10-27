package com.AccountReceivableManagement.cdc.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.AccountReceivableManagement.cdc.config.properties.ClientCdcProperties;
import com.AccountReceivableManagement.cdc.config.properties.ProjectCdcProperties;
import com.AccountReceivableManagement.cdc.listener.ClientCdcHandler;
import com.AccountReceivableManagement.cdc.listener.ProjectCdcHandler;
import com.AccountReceivableManagement.cdc.runner.ClientCdcConfig;
import com.AccountReceivableManagement.cdc.runner.ProjectCdcConfig;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The Debezium engine configuration must never reach the log with a secret in it. Every secret below is a unique sentinel;
 * the tests capture ALL log output (root logger, every level) and assert no sentinel appears anywhere, including in
 * exception text, while the real configuration handed to Debezium still carries the real values (behaviour is unchanged).
 */
class CdcConfigurationLoggingTest {

    // Sentinels: never real credentials, deliberately unusual so a substring match cannot be a coincidence.
    private static final String SRC_PASSWORD = "SENTINEL-SRC-PASSWORD-9f2c41";
    private static final String SRC_USER = "SENTINEL-SRC-USER-5b7e10";
    private static final String AR_PASSWORD = "SENTINEL-AR-PASSWORD-3a8d77";
    private static final String URL_USERINFO_PASSWORD = "SENTINEL-URL-USERINFO-c60e12";
    private static final String URL_QUERY_PASSWORD = "SENTINEL-URL-QUERY-81b9d4";
    private static final String TOKEN = "SENTINEL-TOKEN-e4417a";
    private static final String[] ALL_SENTINELS = {SRC_PASSWORD, SRC_USER, AR_PASSWORD, URL_USERINFO_PASSWORD,
            URL_QUERY_PASSWORD, TOKEN};

    private static final String AR_JDBC_URL_WITH_SECRETS =
            "jdbc:mysql://arUser:" + URL_USERINFO_PASSWORD + "@ar-db.example.invalid:3306/ar_db"
                    + "?user=arUser&password=" + URL_QUERY_PASSWORD + "&sslMode=REQUIRED";

    private ListAppender<ILoggingEvent> appender;
    private Logger root;
    private Level previousLevel;

    @BeforeEach
    void captureLogs() {
        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        previousLevel = root.getLevel();
        root.setLevel(Level.ALL);
        appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        root.detachAppender(appender);
        root.setLevel(previousLevel);
    }

    /** Everything that was logged: formatted message, raw arguments and any exception text. */
    private String captured() {
        return appender.list.stream().map(e -> e.getFormattedMessage()
                        + " " + Arrays.toString(e.getArgumentArray())
                        + " " + (e.getThrowableProxy() == null ? "" : e.getThrowableProxy().getMessage()))
                .collect(Collectors.joining("\n"));
    }

    private void assertNoSentinelLogged() {
        String log = captured();
        assertThat(log).as("captured log must not be empty - otherwise this test proves nothing").isNotBlank();
        for (String sentinel : ALL_SENTINELS) {
            assertThat(log).as("sentinel " + sentinel + " leaked into the log").doesNotContain(sentinel);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The allowlist helper
    // ---------------------------------------------------------------------------------------------------------------

    private static Properties propertiesWithSecrets() {
        Properties p = new Properties();
        p.setProperty("name", "PMS");
        p.setProperty("connector.class", "io.debezium.connector.mysql.MySqlConnector");
        p.setProperty("topic.prefix", "project-cdc");
        p.setProperty("database.include.list", "pms");
        p.setProperty("table.include.list", "pms.projects,pms.project_milestones");
        p.setProperty("snapshot.mode", "when_needed");
        p.setProperty("database.ssl.mode", "REQUIRED");
        p.setProperty("database.server.id", "85745");
        p.setProperty("offset.storage.jdbc.offset.table.name", "debezium_project_offsets");
        p.setProperty("schema.history.internal.jdbc.schema.history.table.name", "debezium_project_schema_history");
        // secrets and connection details - must never be printed
        p.setProperty("database.password", SRC_PASSWORD);
        p.setProperty("database.user", SRC_USER);
        p.setProperty("database.hostname", "pms-db.example.invalid");
        p.setProperty("offset.storage.jdbc.password", AR_PASSWORD);
        p.setProperty("offset.storage.jdbc.url", AR_JDBC_URL_WITH_SECRETS);
        p.setProperty("schema.history.internal.jdbc.password", AR_PASSWORD);
        p.setProperty("schema.history.internal.jdbc.url", AR_JDBC_URL_WITH_SECRETS);
        return p;
    }

    @Test
    void summaryContainsOnlyAllowlistedMetadataAndNeverASecret() {
        CdcConfigurationLogging.logSafeSummary(LoggerFactory.getLogger("test"), "Test Configuration", propertiesWithSecrets());

        String log = captured();
        assertNoSentinelLogged();
        assertThat(log).contains("name = PMS", "table.include.list = pms.projects,pms.project_milestones",
                "topic.prefix = project-cdc", "snapshot.mode = when_needed", "database.ssl.mode = REQUIRED");
        assertThat(log).doesNotContain("database.password", "offset.storage.jdbc.password", "database.user",
                "database.hostname", "jdbc.url");
        assertThat(log).contains("intentionally not logged");
    }

    @Test
    void aSecretPropertyAddedInTheFutureCannotLeakBecauseNothingOutsideTheAllowlistIsRead() {
        Properties p = propertiesWithSecrets();
        p.setProperty("some.future.api.token", TOKEN);
        p.setProperty("database.password.rotation.secret", TOKEN);

        CdcConfigurationLogging.logSafeSummary(LoggerFactory.getLogger("test"), "Future", p);

        assertNoSentinelLogged();
        assertThat(captured()).doesNotContain("some.future.api.token", "rotation");
    }

    @Test
    void allowlistContainsNoCredentialLikeKey() {
        for (String key : CdcConfigurationLogging.SAFE_KEYS) {
            assertThat(key.toLowerCase()).as("allowlisted key " + key)
                    .doesNotContain("password", "secret", "token", "user", "credential", "hostname", "url");
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // safeJdbcTarget: strict validation, a fixed placeholder for anything else, never a partial URL
    // ---------------------------------------------------------------------------------------------------------------

    private static final String PLACEHOLDER = CdcConfigurationLogging.URL_NOT_LOGGED;

    @Test
    void plainUrlsKeepOnlySchemeHostPortAndDatabase() {
        assertThat(CdcConfigurationLogging.safeJdbcTarget("jdbc:mysql://ar-db.example.invalid:3306/ar_db"))
                .isEqualTo("jdbc:mysql://ar-db.example.invalid:3306/ar_db");
        assertThat(CdcConfigurationLogging.safeJdbcTarget("jdbc:mysql://127.0.0.1:3399/ar_demo?sslMode=PREFERRED&serverTimezone=UTC"))
                .isEqualTo("jdbc:mysql://127.0.0.1:3399/ar_demo");
        assertThat(CdcConfigurationLogging.safeJdbcTarget("  jdbc:mysql://localhost:3399/ar_demo  "))
                .as("surrounding whitespace is trimmed").isEqualTo("jdbc:mysql://localhost:3399/ar_demo");
        assertThat(CdcConfigurationLogging.safeJdbcTarget("jdbc:mysql://db.internal/ar_db")).isEqualTo("jdbc:mysql://db.internal/ar_db");
        assertThat(CdcConfigurationLogging.safeJdbcTarget("jdbc:mysql://db.internal:3306")).isEqualTo("jdbc:mysql://db.internal:3306");
        assertThat(CdcConfigurationLogging.safeJdbcTarget("jdbc:mysql://db.internal:3306/")).isEqualTo("jdbc:mysql://db.internal:3306");
        assertThat(CdcConfigurationLogging.safeJdbcTarget("jdbc:mysql://[::1]:3399/ar_demo")).isEqualTo("jdbc:mysql://[::1]:3399/ar_demo");
        assertThat(CdcConfigurationLogging.safeJdbcTarget("jdbc:mariadb:replication://h:3306/db")).isEqualTo("jdbc:mariadb:replication://h:3306/db");
        assertThat(CdcConfigurationLogging.safeJdbcTarget("JDBC:MySQL://H:3306/Db")).as("case is kept as written")
                .isEqualTo("JDBC:MySQL://H:3306/Db");
        assertThat(CdcConfigurationLogging.safeJdbcTarget("jdbc:sqlserver://h:1433;databaseName=x")).isEqualTo("jdbc:sqlserver://h:1433");
        assertThat(CdcConfigurationLogging.safeJdbcTarget(null)).isEqualTo("(not configured)");
        assertThat(CdcConfigurationLogging.safeJdbcTarget("")).isEqualTo("(not configured)");
        assertThat(CdcConfigurationLogging.safeJdbcTarget("   ")).isEqualTo("(not configured)");
    }

    @Test
    void secretsInQueryParametersSemicolonParametersAndFragmentsAreNeverPrinted() {
        String[] urls = {
                "jdbc:mysql://h:3306/db?user=u&password=" + TOKEN,
                "jdbc:mysql://h:3306/db?password=" + TOKEN + "&sslMode=REQUIRED",
                "jdbc:mysql://h:3306/db?password=a?b/c;d#e" + TOKEN,
                "jdbc:mysql://h:3306?password=" + TOKEN,
                "jdbc:sqlserver://h:1433;databaseName=x;user=u;password=" + TOKEN,
                "jdbc:mysql://h:3306/db#" + TOKEN,
                "jdbc:mysql://h:3306/db;" + TOKEN,
                "jdbc:mysql://h:3306/db?" + TOKEN,
        };
        for (String url : urls) {
            String out = CdcConfigurationLogging.safeJdbcTarget(url);
            assertThat(out).as(url).doesNotContain(TOKEN).doesNotContain("password").doesNotContain("user=");
            assertThat(out).as(url).startsWith("jdbc:").doesNotContain("?").doesNotContain(";").doesNotContain("#");
        }
    }

    @Test
    void anyUrlWithAtSignIsRefusedWholeWhateverThePasswordContains() {
        // user-info may hold a password containing ?, ;, / or # that makes the URL impossible to split reliably
        String[] passwords = {TOKEN, TOKEN + "?x", TOKEN + ";x", TOKEN + "/x", TOKEN + "#x", "p@" + TOKEN, TOKEN + "&=", "1234?" + TOKEN,
                "1234/" + TOKEN, "1234;" + TOKEN, "a?b@c" + TOKEN, "%40" + TOKEN, TOKEN + ":", " " + TOKEN, "pä" + TOKEN, ""};
        for (String pw : passwords) {
            String url = "jdbc:mysql://USRTOKEN:" + pw + "@db.example.invalid:3306/ar_db";
            assertThat(CdcConfigurationLogging.safeJdbcTarget(url)).as("password: " + pw).isEqualTo(PLACEHOLDER);
        }
        assertThat(CdcConfigurationLogging.safeJdbcTarget("jdbc:mysql://USRTOKEN@db:3306/x")).as("user only").isEqualTo(PLACEHOLDER);
        assertThat(CdcConfigurationLogging.safeJdbcTarget("jdbc:mysql://:" + TOKEN + "@db:3306/x")).as("password only").isEqualTo(PLACEHOLDER);
        assertThat(CdcConfigurationLogging.safeJdbcTarget("jdbc:mysql://h:3306/db?user=a@b&password=" + TOKEN))
                .as("'@' in a query parameter").isEqualTo(PLACEHOLDER);
        assertThat(CdcConfigurationLogging.safeJdbcTarget(AR_JDBC_URL_WITH_SECRETS)).isEqualTo(PLACEHOLDER);
    }

    @Test
    void everySinglePrintableCharacterInAPasswordNeverMakesAnythingLeak() {
        String userToken = "USRTOKEN";
        String pwHead = "PWHEAD";
        String pwTail = "PWTAIL";
        // every ASCII character, plus space, tab, newline, DEL and a non-ASCII letter, as the "special" character
        StringBuilder specials = new StringBuilder();
        for (char c = 0x20; c <= 0x7F; c++) {
            specials.append(c);
        }
        specials.append('\t').append('\n').append('\r').append('é');
        for (char c : specials.toString().toCharArray()) {
            String[] urls = {
                    // password in the user-info
                    "jdbc:mysql://" + userToken + ":" + pwHead + c + pwTail + "@db.example.invalid:3306/ar_db",
                    "jdbc:mysql://" + userToken + ":" + c + pwHead + pwTail + "@db.example.invalid:3306/ar_db",
                    // password in a query, semicolon parameters or a fragment
                    "jdbc:mysql://db.example.invalid:3306/ar_db?password=" + pwHead + c + pwTail,
                    "jdbc:mysql://db.example.invalid:3306/ar_db;password=" + pwHead + c + pwTail,
                    "jdbc:mysql://db.example.invalid:3306/ar_db#" + pwHead + c + pwTail,
                    // the special character right after the host and inside the database position
                    // (no secret here: a database name is legitimately printed; this checks the character cannot slip through)
                    "jdbc:mysql://db.example.invalid:3306" + c + "ZZZ",
                    "jdbc:mysql://db.example.invalid:3306/DB" + c + "NAME",
            };
            for (String url : urls) {
                String out = CdcConfigurationLogging.safeJdbcTarget(url);
                String what = "char 0x" + Integer.toHexString(c) + " in " + url.replace('\n', '~').replace('\r', '~');
                assertThat(out).as(what).doesNotContain(userToken).doesNotContain(pwHead).doesNotContain(pwTail);
                assertThat(out.equals(PLACEHOLDER) || out.startsWith("jdbc:mysql://db.example.invalid:3306"))
                        .as("output is either the placeholder or the validated target: " + what).isTrue();
            }
        }
    }

    @Test
    void malformedOrUnexpectedUrlsGiveTheFixedPlaceholder() {
        String[] bad = {
                "jdbc:", "jdbc:mysql:", "jdbc:mysql://", "jdbc:mysql:///db", "jdbc:mysql://:3306/db",
                "mysql://host:3306/db", "http://host/db", "host:3306/db", "ftp://u:" + TOKEN + "@h/db",
                "jdbc:mysql://host:99999/db", "jdbc:mysql://host:65536/db", "jdbc:mysql://host:0abc/db", "jdbc:mysql://host:abc/db",
                "jdbc:mysql://host:/db",
                "jdbc:mysql://h1:3306,h2:3306/db", "jdbc:mysql://h1,h2/db", "jdbc:mysql://(host=a,port=1)/db",
                "jdbc:mysql://ho st/db", "jdbc:mysql://host/d b", "jdbc:mysql://host/db extra",
                "jdbc:mysql://ho%73t/db", "jdbc:mysql://host/d%62", "jdbc:mysql://host/db/extra", "jdbc:mysql://host/db\\x",
                "jdbc:mysql://-host/db", "jdbc:mysql://host-/db", "jdbc:mysql://[::1/db", "jdbc:mysql://[zz]/db",
                "jdbc:mysql://host/db\nsecond-line", "jdbc:mysql://host/db\u0000", "jdbc:mysql://höst/db", "jdbc:my sql://host/db",
                "jdbc:1mysql://host/db", "jdbc:mysql://" + "h".repeat(300) + "/db", "jdbc:mysql://host/db?" + "x".repeat(600),
                "jdbc:mysql://host/" + "d".repeat(129),
        };
        for (String url : bad) {
            String out = CdcConfigurationLogging.safeJdbcTarget(url);
            assertThat(out).as(url.length() > 80 ? url.substring(0, 80) + "..." : url).isEqualTo(PLACEHOLDER);
            assertThat(out).doesNotContain(TOKEN).doesNotContain("://");
        }
    }

    @Test
    void thePlaceholderIsFixedAndContainsNothingFromTheInput() {
        assertThat(PLACEHOLDER).doesNotContain("jdbc:mysql").doesNotContain("@").doesNotContain("://");
        assertThat(CdcConfigurationLogging.safeJdbcTarget("jdbc:mysql://a:b@c/d")).isSameAs(PLACEHOLDER);
        assertThat(CdcConfigurationLogging.safeJdbcTarget("jdbc:mysql://x:y@z/w")).isSameAs(PLACEHOLDER);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The real configuration beans: nothing leaks, behaviour is unchanged
    // ---------------------------------------------------------------------------------------------------------------

    private static ProjectCdcProperties projectProperties() {
        ProjectCdcProperties p = new ProjectCdcProperties();
        p.setHostname("pms-db.example.invalid");
        p.setPort(3306);
        p.setDatabaseName("pms");
        p.setUsername(SRC_USER);
        p.setPassword(SRC_PASSWORD);
        p.setTableIncludeList("pms.projects,pms.project_milestones");
        return p;
    }

    private static ClientCdcProperties clientProperties() {
        ClientCdcProperties p = new ClientCdcProperties();
        p.setHostname("rms-db.example.invalid");
        p.setPort(3306);
        p.setDatabaseName("rms");
        p.setUsername(SRC_USER);
        p.setPassword(SRC_PASSWORD);
        p.setTableIncludeList("rms.client");
        return p;
    }

    private static ProjectDebeziumConfig projectConfig() {
        ProjectDebeziumConfig config = new ProjectDebeziumConfig(projectProperties());
        ReflectionTestUtils.setField(config, "arJdbcUrl", AR_JDBC_URL_WITH_SECRETS);
        ReflectionTestUtils.setField(config, "arUsername", "arUser");
        ReflectionTestUtils.setField(config, "arPassword", AR_PASSWORD);
        return config;
    }

    private static ClientDebeziumConfig clientConfig() {
        ClientDebeziumConfig config = new ClientDebeziumConfig(clientProperties());
        ReflectionTestUtils.setField(config, "arJdbcUrl", AR_JDBC_URL_WITH_SECRETS);
        ReflectionTestUtils.setField(config, "arUsername", "arUser");
        ReflectionTestUtils.setField(config, "arPassword", AR_PASSWORD);
        return config;
    }

    @Test
    void projectDebeziumConfigurationLogsNoSecretButStillBuildsTheRealConfiguration() {
        ProjectDebeziumConfig config = projectConfig();
        appender.list.clear(); // the fixture setup (ReflectionTestUtils) logs field values itself at DEBUG; capture only the code under test

        io.debezium.config.Configuration configuration = config.projectDebeziumConfiguration();

        assertNoSentinelLogged();
        assertThat(captured()).contains(PLACEHOLDER); // the fixture URL carries user-info, so nothing of it is printed
        // behaviour unchanged: Debezium still receives the real credentials and URL
        assertThat(configuration.getString("database.password")).isEqualTo(SRC_PASSWORD);
        assertThat(configuration.getString("database.user")).isEqualTo(SRC_USER);
        assertThat(configuration.getString("offset.storage.jdbc.password")).isEqualTo(AR_PASSWORD);
        assertThat(configuration.getString("schema.history.internal.jdbc.password")).isEqualTo(AR_PASSWORD);
        assertThat(configuration.getString("offset.storage.jdbc.url")).isEqualTo(AR_JDBC_URL_WITH_SECRETS);
        assertThat(configuration.getString("table.include.list")).isEqualTo("pms.projects,pms.project_milestones");
    }

    @Test
    void clientDebeziumConfigurationLogsNoSecretButStillBuildsTheRealConfiguration() {
        ClientDebeziumConfig config = clientConfig();
        appender.list.clear(); // see above: only the code under test is captured

        io.debezium.config.Configuration configuration = config.clientDebeziumConfiguration();

        assertNoSentinelLogged();
        assertThat(captured()).contains(PLACEHOLDER); // the fixture URL carries user-info, so nothing of it is printed
        assertThat(configuration.getString("database.password")).isEqualTo(SRC_PASSWORD);
        assertThat(configuration.getString("offset.storage.jdbc.password")).isEqualTo(AR_PASSWORD);
        assertThat(configuration.getString("offset.storage.jdbc.url")).isEqualTo(AR_JDBC_URL_WITH_SECRETS);
        assertThat(configuration.getString("table.include.list")).isEqualTo("rms.client");
    }

    @Test
    void hostileUserInfoUrlsAreNeverLoggedByEitherConfigurationYetReachDebeziumUnchanged() {
        String[] hostile = {
                "jdbc:mysql://arUser:" + URL_USERINFO_PASSWORD + "?x@ar-db.example.invalid:3306/ar_db",
                "jdbc:mysql://arUser:" + URL_USERINFO_PASSWORD + ";x@ar-db.example.invalid:3306/ar_db",
                "jdbc:mysql://arUser:" + URL_USERINFO_PASSWORD + "/x@ar-db.example.invalid:3306/ar_db",
                "jdbc:mysql://arUser:" + URL_USERINFO_PASSWORD + "#x@ar-db.example.invalid:3306/ar_db",
        };
        for (String url : hostile) {
            ProjectDebeziumConfig project = projectConfig();
            ReflectionTestUtils.setField(project, "arJdbcUrl", url);
            appender.list.clear();
            io.debezium.config.Configuration projectConfiguration = project.projectDebeziumConfiguration();
            assertNoSentinelLogged();
            assertThat(captured()).as(url).contains(PLACEHOLDER);
            assertThat(projectConfiguration.getString("offset.storage.jdbc.url")).as("connector still gets the real URL").isEqualTo(url);
            assertThat(projectConfiguration.getString("schema.history.internal.jdbc.url")).isEqualTo(url);

            ClientDebeziumConfig client = clientConfig();
            ReflectionTestUtils.setField(client, "arJdbcUrl", url);
            appender.list.clear();
            io.debezium.config.Configuration clientConfiguration = client.clientDebeziumConfiguration();
            assertNoSentinelLogged();
            assertThat(captured()).as(url).contains(PLACEHOLDER);
            assertThat(clientConfiguration.getString("offset.storage.jdbc.url")).isEqualTo(url);
            assertThat(clientConfiguration.getString("schema.history.internal.jdbc.url")).isEqualTo(url);
        }
    }

    @Test
    void aPasswordOnlyInTheQueryLogsTheSafeTargetAndReachesDebeziumUnchanged() {
        String url = "jdbc:mysql://ar-db.example.invalid:3306/ar_db?user=arUser&password=" + URL_QUERY_PASSWORD + "&sslMode=REQUIRED";

        ProjectDebeziumConfig project = projectConfig();
        ReflectionTestUtils.setField(project, "arJdbcUrl", url);
        appender.list.clear();
        io.debezium.config.Configuration projectConfiguration = project.projectDebeziumConfiguration();
        assertNoSentinelLogged();
        assertThat(captured()).contains("jdbc:mysql://ar-db.example.invalid:3306/ar_db").doesNotContain("sslMode").doesNotContain("user=");
        assertThat(projectConfiguration.getString("offset.storage.jdbc.url")).isEqualTo(url);

        ClientDebeziumConfig client = clientConfig();
        ReflectionTestUtils.setField(client, "arJdbcUrl", url);
        appender.list.clear();
        io.debezium.config.Configuration clientConfiguration = client.clientDebeziumConfiguration();
        assertNoSentinelLogged();
        assertThat(captured()).contains("jdbc:mysql://ar-db.example.invalid:3306/ar_db").doesNotContain("sslMode").doesNotContain("user=");
        assertThat(clientConfiguration.getString("offset.storage.jdbc.url")).isEqualTo(url);
    }

    @Test
    void projectEngineBeanLogsNoSecretAndIsNotStarted() throws Exception {
        io.debezium.config.Configuration configuration = projectConfig().projectDebeziumConfiguration();
        appender.list.clear(); // isolate what the ENGINE bean logs

        DebeziumEngine<ChangeEvent<String, String>> engine =
                new ProjectCdcConfig().projectDebeziumEngine(configuration, mock(ProjectCdcHandler.class));

        assertThat(engine).isNotNull();
        assertNoSentinelLogged();
        assertThat(captured()).contains("Project Debezium Configuration", "table.include.list = pms.projects,pms.project_milestones");
        engine.close(); // never run(): no connector is started, nothing connects
    }

    @Test
    void clientEngineBeanLogsNoSecretAndIsNotStarted() throws Exception {
        io.debezium.config.Configuration configuration = clientConfig().clientDebeziumConfiguration();
        appender.list.clear();

        DebeziumEngine<ChangeEvent<String, String>> engine =
                new ClientCdcConfig().debeziumEngine(configuration, mock(ClientCdcHandler.class));

        assertThat(engine).isNotNull();
        assertNoSentinelLogged();
        assertThat(captured()).contains("Debezium Configuration", "table.include.list = rms.client");
        engine.close();
    }
}
