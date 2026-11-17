package com.AccountReceivableManagement.integration.ums;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.AccountReceivableManagement.config.properties.UmsServiceAuthProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UMS service-token lifecycle against a loopback UMS stub: login contract on
 * the wire, caching and renewal by the token's own expiry, one login for
 * concurrent callers, fail-closed configuration, credential rejection with
 * cooldown, malformed responses - and that neither the password nor the token
 * ever appears in a log line or exception message.
 */
class UmsServiceTokenProviderTest {

    private static final String PASSWORD = "S3rvice-Pa55word!";
    private static final String EMAIL = "ar-billing-service@paves.example";
    private static final Instant T0 = Instant.parse("2026-10-23T08:00:00Z");

    private HttpServer server;
    private final AtomicInteger logins = new AtomicInteger();
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private final AtomicReference<String> lastContentType = new AtomicReference<>();
    private final MutableClock clock = new MutableClock(T0);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        ((Logger) LoggerFactory.getLogger(UmsServiceTokenProvider.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(UmsServiceTokenProvider.class)).detachAppender(logs);
        if (server != null) {
            server.stop(0);
        }
    }

    // ================================================================ stub and helpers

    private record Reply(int status, String body, long delayMillis) {
        static Reply of(int status, String body) {
            return new Reply(status, body, 0);
        }
    }

    /** UMS stub; {@code replies} receives the 1-based login number. Returns the login URL. */
    private String umsAnswering(IntFunction<Reply> replies) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/ums/auth/login", exchange -> {
            int number = logins.incrementAndGet();
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            Reply reply = replies.apply(number);
            if (reply.delayMillis() > 0) {
                try {
                    Thread.sleep(reply.delayMillis());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/ums/auth/login";
    }

    private static UmsServiceAuthProperties properties(String loginUrl) {
        UmsServiceAuthProperties properties = new UmsServiceAuthProperties();
        properties.setEnabled(true);
        properties.setLoginUrl(loginUrl);
        properties.setEmail(EMAIL);
        properties.setPassword(PASSWORD);
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(2));
        properties.setRefreshBeforeExpiry(Duration.ofMinutes(2));
        properties.setCredentialRejectionCooldown(Duration.ofMinutes(15));
        return properties;
    }

    private UmsServiceTokenProvider provider(String loginUrl) {
        return new UmsServiceTokenProvider(properties(loginUrl), clock);
    }

    /** An unsigned JWT-shaped token expiring at {@code exp} (UMS signs real ones; AR only reads exp). */
    private static String jwt(String subject, Instant exp) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8))
                + "." + encoder.encodeToString(("{\"sub\":\"" + subject + "\",\"exp\":" + exp.getEpochSecond() + "}")
                        .getBytes(StandardCharsets.UTF_8))
                + ".c2lnbmF0dXJl";
    }

    private static Reply token(String accessToken) {
        return Reply.of(200, "{\"access_token\":\"" + accessToken + "\",\"token_type\":\"Bearer\"}");
    }

    private void assertNoSecretsLogged(String... tokens) {
        for (ILoggingEvent event : logs.list) {
            String line = event.getFormattedMessage();
            assertThat(line).doesNotContain(PASSWORD).doesNotContain(EMAIL);
            for (String token : tokens) {
                assertThat(line).doesNotContain(token);
            }
        }
    }

    // ================================================================ configuration (fail closed)

    @Test
    void disabled_isNotUsable_andNeverCallsUms() throws IOException {
        String url = umsAnswering(n -> token("unused"));
        UmsServiceAuthProperties properties = properties(url);
        properties.setEnabled(false);
        UmsServiceTokenProvider provider = new UmsServiceTokenProvider(properties, clock);

        assertThat(provider.isEnabled()).isFalse();
        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(UmsAuthenticationException.class)
                .hasMessageContaining("not enabled");
        assertThat(logins).hasValue(0);
    }

    @Test
    void enabledWithoutCredentials_failsAtStartup_namingWhatIsMissing() {
        UmsServiceAuthProperties properties = properties("https://ums.example/ums/auth/login");
        properties.setEmail(" ");
        properties.setPassword(null);

        assertThatThrownBy(() -> new UmsServiceTokenProvider(properties, clock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UMS_SERVICE_EMAIL")
                .hasMessageContaining("UMS_SERVICE_PASSWORD")
                .hasMessageNotContaining("UMS_LOGIN_URL");
    }

    @Test
    void enabledWithoutLoginUrl_failsAtStartup() {
        UmsServiceAuthProperties properties = properties("");

        assertThatThrownBy(() -> new UmsServiceTokenProvider(properties, clock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UMS_LOGIN_URL");
    }

    @Test
    void plainHttpToANonLoopbackHost_isRefused_theRequestCarriesThePassword() {
        UmsServiceAuthProperties properties = properties("http://ums.example.com/ums/auth/login");

        assertThatThrownBy(() -> new UmsServiceTokenProvider(properties, clock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("https")
                .hasMessageNotContaining(PASSWORD);
    }

    // ================================================================ login and caching

    @Test
    void login_postsTheServiceAccountCredentialsAsJson_andReturnsTheAccessToken() throws IOException {
        String accessToken = jwt("svc", T0.plus(Duration.ofHours(1)));
        UmsServiceTokenProvider provider = provider(umsAnswering(n -> token(accessToken)));

        assertThat(provider.getAccessToken()).isEqualTo(accessToken);
        assertThat(lastContentType.get()).startsWith("application/json");
        assertThat(lastRequestBody.get()).contains("\"email\":\"" + EMAIL + "\"")
                .contains("\"password\":\"" + PASSWORD + "\"");
        assertNoSecretsLogged(accessToken);
    }

    @Test
    void validToken_isReused_withoutAnotherLogin() throws IOException {
        String accessToken = jwt("svc", T0.plus(Duration.ofHours(1)));
        UmsServiceTokenProvider provider = provider(umsAnswering(n -> token(accessToken)));

        provider.getAccessToken();
        clock.advance(Duration.ofMinutes(50));
        assertThat(provider.getAccessToken()).isEqualTo(accessToken);

        assertThat(logins).hasValue(1);
    }

    @Test
    void tokenNearItsExpiry_isRenewedBeforeItExpires() throws IOException {
        UmsServiceTokenProvider provider = provider(umsAnswering(
                n -> token(jwt("svc-" + n, T0.plus(Duration.ofHours(n))))));

        String first = provider.getAccessToken();
        // Expires at T0+1h; renewed from 2 minutes before.
        clock.advance(Duration.ofMinutes(58).plusSeconds(1));
        String second = provider.getAccessToken();

        assertThat(second).isNotEqualTo(first);
        assertThat(logins).hasValue(2);
    }

    @Test
    void expiredToken_isReacquired() throws IOException {
        UmsServiceTokenProvider provider = provider(umsAnswering(
                n -> token(jwt("svc-" + n, T0.plus(Duration.ofHours(2L * n))))));

        provider.getAccessToken();
        clock.advance(Duration.ofHours(3));
        provider.getAccessToken();

        assertThat(logins).hasValue(2);
    }

    @Test
    void shortLivedToken_isStillReused_forHalfItsLifetime() throws IOException {
        // 60-second token: the 2-minute renewal margin is capped at half its lifetime.
        UmsServiceTokenProvider provider = provider(umsAnswering(
                n -> token(jwt("svc-" + n, T0.plusSeconds(60)))));

        provider.getAccessToken();
        clock.advance(Duration.ofSeconds(20));
        provider.getAccessToken();

        assertThat(logins).hasValue(1);
    }

    @Test
    void expiresInShorterThanTheJwtExpiry_wins() throws IOException {
        String accessToken = jwt("svc", T0.plus(Duration.ofHours(10)));
        UmsServiceTokenProvider provider = provider(umsAnswering(n -> Reply.of(200,
                "{\"access_token\":\"" + accessToken + "\",\"expires_in\":600}")));

        provider.getAccessToken();
        clock.advance(Duration.ofMinutes(9));
        provider.getAccessToken();

        assertThat(logins).hasValue(2);
    }

    @Test
    void tokenWithoutAnyExpiry_isNotCached_noExpiryIsInvented() throws IOException {
        UmsServiceTokenProvider provider = provider(umsAnswering(n -> token("opaque-token-" + n)));

        assertThat(provider.getAccessToken()).isEqualTo("opaque-token-1");
        assertThat(provider.getAccessToken()).isEqualTo("opaque-token-2");
        assertThat(logins).hasValue(2);
    }

    @Test
    void alreadyExpiredToken_isRejected() throws IOException {
        UmsServiceTokenProvider provider = provider(umsAnswering(n -> token(jwt("svc", T0.minusSeconds(5)))));

        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(UmsAuthenticationException.class)
                .hasMessageContaining("already expired");
    }

    @Test
    void invalidatedToken_isReplacedByAFreshLogin() throws IOException {
        UmsServiceTokenProvider provider = provider(umsAnswering(
                n -> token(jwt("svc-" + n, T0.plus(Duration.ofHours(1))))));

        String refused = provider.getAccessToken();
        provider.invalidate(refused);
        String fresh = provider.getAccessToken();

        assertThat(fresh).isNotEqualTo(refused);
        assertThat(logins).hasValue(2);
    }

    @Test
    void invalidatingAnOutdatedToken_keepsTheCurrentOne() throws IOException {
        UmsServiceTokenProvider provider = provider(umsAnswering(
                n -> token(jwt("svc-" + n, T0.plus(Duration.ofHours(1))))));

        String first = provider.getAccessToken();
        provider.invalidate(first);
        String second = provider.getAccessToken();
        provider.invalidate(first);

        assertThat(provider.getAccessToken()).isEqualTo(second);
        assertThat(logins).hasValue(2);
    }

    @Test
    void concurrentCallers_shareOneLogin() throws Exception {
        String accessToken = jwt("svc", T0.plus(Duration.ofHours(1)));
        UmsServiceTokenProvider provider = provider(umsAnswering(
                n -> new Reply(200, "{\"access_token\":\"" + accessToken + "\"}", 300)));

        int callers = 16;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return provider.getAccessToken();
            }));
        }
        start.countDown();
        Set<String> tokens = ConcurrentHashMap.newKeySet();
        for (Future<String> result : results) {
            tokens.add(result.get(10, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        assertThat(tokens).containsExactly(accessToken);
        assertThat(logins).hasValue(1);
    }

    // ================================================================ failures

    @Test
    void rejectedCredentials_failPermanently_andSuspendLoginsForTheCooldown() throws IOException {
        UmsServiceTokenProvider provider = provider(umsAnswering(
                n -> Reply.of(401, "{\"message\":\"Invalid email or password for " + EMAIL + "\"}")));

        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(UmsAuthenticationException.class)
                .hasMessageContaining("status 401")
                .hasMessageNotContaining(EMAIL)
                .hasMessageNotContaining(PASSWORD);
        // No retry loop: further calls within the cooldown do not reach UMS.
        assertThatThrownBy(provider::getAccessToken).isInstanceOf(UmsAuthenticationException.class)
                .hasMessageContaining("no new login is attempted");
        assertThat(logins).hasValue(1);

        clock.advance(Duration.ofMinutes(16));
        assertThatThrownBy(provider::getAccessToken).isInstanceOf(UmsAuthenticationException.class);
        assertThat(logins).hasValue(2);
        assertNoSecretsLogged();
    }

    @Test
    void forbiddenLogin_isTreatedAsRejectedCredentials() throws IOException {
        UmsServiceTokenProvider provider = provider(umsAnswering(n -> Reply.of(403, "")));

        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(UmsAuthenticationException.class)
                .hasMessageContaining("status 403");
    }

    @Test
    void malformedResponse_failsWithoutEchoingTheBody() throws IOException {
        UmsServiceTokenProvider provider = provider(umsAnswering(
                n -> Reply.of(200, "{\"access_token\":\"half-a-secret-tok")));

        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(UmsAuthenticationException.class)
                .hasMessageContaining("not a JSON object")
                .hasMessageNotContaining("half-a-secret");
        assertNoSecretsLogged("half-a-secret");
    }

    @Test
    void responseWithoutAccessToken_fails() throws IOException {
        UmsServiceTokenProvider provider = provider(umsAnswering(
                n -> Reply.of(200, "{\"data\":{\"token\":\"elsewhere\"}}")));

        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(UmsAuthenticationException.class)
                .hasMessage("UMS login response did not contain an access_token.");
    }

    @Test
    void blankAccessToken_fails() throws IOException {
        UmsServiceTokenProvider provider = provider(umsAnswering(n -> Reply.of(200, "{\"access_token\":\"  \"}")));

        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(UmsAuthenticationException.class)
                .hasMessage("UMS login response did not contain an access_token.");
    }

    @Test
    void umsServerError_isTemporary_andDoesNotSuspendLogins() throws IOException {
        String accessToken = jwt("svc", T0.plus(Duration.ofHours(1)));
        UmsServiceTokenProvider provider = provider(umsAnswering(
                n -> n == 1 ? Reply.of(503, "") : token(accessToken)));

        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(UmsServiceUnavailableException.class)
                .hasMessageContaining("status 503");
        assertThat(provider.getAccessToken()).isEqualTo(accessToken);
    }

    @Test
    void umsRateLimit_isTemporary() throws IOException {
        UmsServiceTokenProvider provider = provider(umsAnswering(n -> Reply.of(429, "")));

        assertThatThrownBy(provider::getAccessToken).isInstanceOf(UmsServiceUnavailableException.class);
    }

    @Test
    void unreachableUms_isTemporary() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        UmsServiceTokenProvider provider = provider("http://127.0.0.1:" + closedPort + "/ums/auth/login");

        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(UmsServiceUnavailableException.class)
                .hasMessageContaining("could not be completed");
    }

    @Test
    void umsThatNeverAnswers_timesOut() throws IOException {
        UmsServiceTokenProvider provider = provider(umsAnswering(n -> new Reply(200, "{}", 4_000)));

        long startedAt = System.nanoTime();
        assertThatThrownBy(provider::getAccessToken).isInstanceOf(UmsServiceUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(4));
    }

    // ================================================================ clock

    private static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
