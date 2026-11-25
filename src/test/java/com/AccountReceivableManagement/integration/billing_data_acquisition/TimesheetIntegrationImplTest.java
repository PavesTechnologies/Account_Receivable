package com.AccountReceivableManagement.integration.billing_data_acquisition;

import com.AccountReceivableManagement.config.properties.UmsServiceAuthProperties;
import com.AccountReceivableManagement.dto.billing_data_acquisition.TimesheetDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionResultDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.ValidationResultDto;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotItemRepository;
import com.AccountReceivableManagement.validator.billing_data_acquisition.BillingAcquisitionValidator;
import com.AccountReceivableManagement.integration.ums.UmsAuthenticationException;
import com.AccountReceivableManagement.integration.ums.UmsServiceTokenProvider;
import com.AccountReceivableManagement.security.ActorContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TMS-to-AR mapping: approval and billability come from TMS's own values,
 * never forced, so the validator sees the real source data. The HTTP tests
 * run against a local stub server: the exact period, the Authorization
 * actually sent (user JWT, UMS service token, or none at all), retries and
 * response verification are checked on the wire.
 */
class TimesheetIntegrationImplTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 5);
    private static final LocalDate END = LocalDate.of(2026, 10, 11);

    /** No real waiting in tests; the waits asked for are recorded instead. */
    private static final TimesheetIntegrationImpl.RetrySettings RETRY =
            new TimesheetIntegrationImpl.RetrySettings(3, Duration.ofSeconds(1), Duration.ofSeconds(10));

    private final TimesheetIntegrationImpl integration = new TimesheetIntegrationImpl("http://tms.local");

    private HttpServer server;
    private final AtomicReference<String> receivedQuery = new AtomicReference<>();
    private final AtomicReference<String> receivedAuthorization = new AtomicReference<>();
    private final List<String> authorizationsSent = new CopyOnWriteArrayList<>();
    private final AtomicInteger tmsCalls = new AtomicInteger();
    private final AtomicInteger umsLogins = new AtomicInteger();
    private final List<Duration> waits = new CopyOnWriteArrayList<>();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        if (server != null) {
            server.stop(0);
        }
    }

    // ================================================================ stubs

    private record Reply(int status, String body, Map<String, String> headers) {
        static Reply of(int status, String body) {
            return new Reply(status, body, Map.of());
        }
    }

    /** Starts a TMS stub answering {@code /tms/api/timesheets/billing}; returns the base URL. */
    private String tmsAnswering(int status, String body) throws IOException {
        return tmsAnswering((call, authorization) -> Reply.of(status, body));
    }

    /** TMS stub whose reply depends on the 1-based call number and the Authorization sent. */
    private String tmsAnswering(BiFunction<Integer, String, Reply> replies) throws IOException {
        ensureServer();
        server.createContext("/tms/api/timesheets/billing", exchange -> {
            int call = tmsCalls.incrementAndGet();
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            receivedQuery.set(exchange.getRequestURI().getQuery());
            receivedAuthorization.set(authorization);
            authorizationsSent.add(String.valueOf(authorization));
            reply(exchange, replies.apply(call, authorization));
        });
        return baseUrl() + "/tms";
    }

    /** UMS stub issuing {@code svc-token-<n>} on the n-th login, valid for an hour. */
    private UmsServiceTokenProvider umsIssuingTokens() throws IOException {
        ensureServer();
        server.createContext("/ums/auth/login", exchange -> {
            int login = umsLogins.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            reply(exchange, Reply.of(200, "{\"access_token\":\"" + jwt("svc-token-" + login) + "\"}"));
        });
        UmsServiceAuthProperties properties = new UmsServiceAuthProperties();
        properties.setEnabled(true);
        properties.setLoginUrl(baseUrl() + "/ums/auth/login");
        properties.setEmail("ar-service@paves.example");
        properties.setPassword("not-a-real-password");
        return new UmsServiceTokenProvider(properties);
    }

    /** JWT-shaped token whose subject identifies it in assertions. */
    private static String jwt(String subject) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(("{\"sub\":\"" + subject + "\",\"exp\":"
                        + Instant.now().plus(Duration.ofHours(1)).getEpochSecond() + "}")
                        .getBytes(StandardCharsets.UTF_8))
                + ".sig";
    }

    private static boolean carriesToken(String authorization, String subject) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        String[] parts = authorization.substring(7).split("\\.");
        return parts.length == 3 && new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .contains("\"sub\":\"" + subject + "\"");
    }

    private void ensureServer() throws IOException {
        if (server == null) {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.start();
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void reply(HttpExchange exchange, Reply reply) throws IOException {
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        reply.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    private TimesheetIntegrationImpl tms(String baseUrl, String serviceToken, UmsServiceTokenProvider ums) {
        return new TimesheetIntegrationImpl(baseUrl, Duration.ofSeconds(2), Duration.ofSeconds(2), serviceToken, ums,
                RETRY, waits::add);
    }

    private static final String TWO_TIMESHEETS = """
            {"projectId":7,"billingPeriodStart":"2026-10-05","billingPeriodEnd":"2026-10-11","timesheets":[
              {"timesheetId":"TS-1","resourceId":1,"resourceName":"Asha","workDate":"2026-10-06","hours":8,
               "role":"Developer","approvalStatus":"APPROVED","billable":true},
              {"timesheetId":"TS-2","resourceId":2,"resourceName":"Ravi","workDate":"2026-10-07","hours":5,
               "role":"Senior Developer","approvalStatus":"SUBMITTED","billable":false}]}
            """;

    private TmsTimesheetEntry entry(String approvalStatus, Boolean billable) {
        return TmsTimesheetEntry.builder()
                .timesheetId("TS-42")
                .resourceId(9L)
                .resourceName("Ravi Kumar")
                .workDate(LocalDate.of(2026, 10, 7))
                .hours(new BigDecimal("7.5"))
                .role("Senior Developer")
                .approvalStatus(approvalStatus)
                .billable(billable)
                .build();
    }

    @Test
    void approvedBillableEntry_mapsEveryFieldAsSent() {
        TimesheetDto dto = integration.toTimesheetDto(entry("APPROVED", true));

        assertThat(dto.getSourceReferenceId()).isEqualTo("TS-42");
        assertThat(dto.getResourceId()).isEqualTo(9L);
        assertThat(dto.getResourceName()).isEqualTo("Ravi Kumar");
        assertThat(dto.getWorkDate()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(dto.getHours()).isEqualByComparingTo("7.5");
        assertThat(dto.getRole()).isEqualTo("Senior Developer");
        assertThat(dto.getApprovalStatus()).isEqualTo("APPROVED");
        assertThat(dto.isApproved()).isTrue();
        assertThat(dto.isBillable()).isTrue();
        assertThat(dto.getHourlyRate()).isNull();
    }

    @Test
    void unapprovedEntry_isNotForcedToApproved() {
        TimesheetDto dto = integration.toTimesheetDto(entry("SUBMITTED", true));

        assertThat(dto.getApprovalStatus()).isEqualTo("SUBMITTED");
        assertThat(dto.isApproved()).isFalse();
    }

    @Test
    void approvalStatusIsMatchedCaseInsensitively() {
        assertThat(integration.toTimesheetDto(entry(" approved ", true)).isApproved()).isTrue();
    }

    @Test
    void nonBillableEntry_isNotForcedToBillable() {
        assertThat(integration.toTimesheetDto(entry("APPROVED", false)).isBillable()).isFalse();
    }

    @Test
    void missingApproval_isNotTreatedAsApproved() {
        TimesheetDto dto = integration.toTimesheetDto(entry(null, null));

        assertThat(dto.isApproved()).isFalse();
    }

    @Test
    void billingEndpointRecordWithoutAFlag_isBillableByTheEndpointContract() {
        TimesheetDto dto = integration.toTimesheetDto(entry("APPROVED", null));

        assertThat(dto.isBillable()).isTrue();
    }

    @Test
    void isBillableEntry_mapsAsBillable() {
        TmsTimesheetEntry entry = entry("APPROVED", null);
        entry.setIsBillable(true);

        TimesheetDto dto = integration.toTimesheetDto(entry);

        assertThat(dto.isBillable()).isTrue();
    }

    @Test
    void explicitIsBillableFalse_isNeverOverriddenByTheContract() {
        TmsTimesheetEntry entry = entry("APPROVED", null);
        entry.setIsBillable(false);

        assertThat(integration.toTimesheetDto(entry).isBillable()).isFalse();
    }

    // ================================================================ HTTP call

    @Test
    void callsTmsForExactlyTheRequestedPeriod_andMapsTheResponseAsSent() throws IOException {
        callerSends("Bearer user-jwt");
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200, TWO_TIMESHEETS));

        List<TimesheetDto> timesheets = tms.getApprovedTimesheets(7L, START, END);

        assertThat(receivedQuery.get())
                .isEqualTo("projectId=7&billingPeriodStart=2026-10-05&billingPeriodEnd=2026-10-11");
        assertThat(timesheets).extracting(TimesheetDto::getSourceReferenceId).containsExactly("TS-1", "TS-2");
        // The second entry is passed on as TMS sent it - the validator rejects it.
        assertThat(timesheets.get(1).isApproved()).isFalse();
        assertThat(timesheets.get(1).isBillable()).isFalse();
    }

    @Test
    void withinAnHttpRequest_forwardsTheCallersAuthorization() throws IOException {
        callerSends("Bearer user-jwt");
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200, TWO_TIMESHEETS));

        tms.getApprovedTimesheets(7L, START, END);

        assertThat(receivedAuthorization.get()).isEqualTo("Bearer user-jwt");
    }

    @Test
    void onASchedulerThread_withoutAnyServiceCredential_failsClosed_withoutCallingTms() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200, TWO_TIMESHEETS));

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TMS was not called")
                .hasMessageContaining("service-to-service authentication");
        assertThat(tmsCalls).hasValue(0);
    }

    @Test
    void onASchedulerThread_withServiceToken_sendsBearerAuthorization() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200, TWO_TIMESHEETS), "svc-token-xyz");

        List<TimesheetDto> timesheets = tms.getApprovedTimesheets(7L, START, END);

        assertThat(receivedAuthorization.get()).isEqualTo("Bearer svc-token-xyz");
        assertThat(timesheets).hasSize(2);
    }

    @Test
    void onASchedulerThread_withServiceToken_tmsRefusal_reportsInvalidServiceToken() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(401, ""), "invalid-svc-token");

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TMS authentication failed: service-to-service token is invalid or expired.");
    }

    @Test
    void withACallerJwt_tmsRefusal_reportsTheInvalidJwt() throws IOException {
        callerSends("Bearer expired");
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(401, ""));

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .hasMessage("TMS authentication failed: the JWT is invalid or expired.");
    }

    // ================================================================ UMS service token

    @Test
    void onASchedulerThread_withUmsServiceAuth_sendsTheUmsTokenAsBearer() throws IOException {
        String tmsUrl = tmsAnswering(200, TWO_TIMESHEETS);
        TimesheetIntegrationImpl tms = tms(tmsUrl, null, umsIssuingTokens());

        assertThat(tms.getApprovedTimesheets(7L, START, END)).hasSize(2);
        assertThat(carriesToken(receivedAuthorization.get(), "svc-token-1")).isTrue();
    }

    @Test
    void umsServiceAuth_takesPrecedenceOverTheStaticServiceToken() throws IOException {
        String tmsUrl = tmsAnswering(200, TWO_TIMESHEETS);
        TimesheetIntegrationImpl tms = tms(tmsUrl, "legacy-static-token", umsIssuingTokens());

        tms.getApprovedTimesheets(7L, START, END);

        assertThat(carriesToken(receivedAuthorization.get(), "svc-token-1")).isTrue();
    }

    @Test
    void umsToken_isReusedAcrossCalls() throws IOException {
        String tmsUrl = tmsAnswering(200, TWO_TIMESHEETS);
        TimesheetIntegrationImpl tms = tms(tmsUrl, null, umsIssuingTokens());

        tms.getApprovedTimesheets(7L, START, END);
        tms.getApprovedTimesheets(7L, START, END);

        assertThat(umsLogins).hasValue(1);
        assertThat(tmsCalls).hasValue(2);
    }

    @Test
    void theSystemInsideAUserRequest_usesTheServiceCredential_notTheUsersJwt() throws IOException {
        callerSends("Bearer user-jwt");
        String tmsUrl = tmsAnswering(200, TWO_TIMESHEETS);
        TimesheetIntegrationImpl tms = tms(tmsUrl, null, umsIssuingTokens());

        ActorContext.callAsSystem(() -> tms.getApprovedTimesheets(7L, START, END));

        assertThat(carriesToken(receivedAuthorization.get(), "svc-token-1")).isTrue();
    }

    @Test
    void userRequest_withUmsServiceAuthEnabled_stillForwardsTheUsersJwt() throws IOException {
        callerSends("Bearer user-jwt");
        String tmsUrl = tmsAnswering(200, TWO_TIMESHEETS);
        TimesheetIntegrationImpl tms = tms(tmsUrl, null, umsIssuingTokens());

        tms.getApprovedTimesheets(7L, START, END);

        assertThat(receivedAuthorization.get()).isEqualTo("Bearer user-jwt");
        assertThat(umsLogins).hasValue(0);
    }

    @Test
    void refusedUmsToken_isReplacedByOneFreshLogin_andTheCallSucceeds() throws IOException {
        String tmsUrl = tmsAnswering((call, authorization) -> carriesToken(authorization, "svc-token-1")
                ? Reply.of(401, "")
                : Reply.of(200, TWO_TIMESHEETS));
        TimesheetIntegrationImpl tms = tms(tmsUrl, null, umsIssuingTokens());

        assertThat(tms.getApprovedTimesheets(7L, START, END)).hasSize(2);
        assertThat(umsLogins).hasValue(2);
        assertThat(tmsCalls).hasValue(2);
        assertThat(carriesToken(receivedAuthorization.get(), "svc-token-2")).isTrue();
    }

    @Test
    void umsTokenRefusedAgainAfterReauthentication_failsWithoutFurtherAttempts() throws IOException {
        String tmsUrl = tmsAnswering(401, "");
        TimesheetIntegrationImpl tms = tms(tmsUrl, null, umsIssuingTokens());

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("even after re-authenticating");
        assertThat(umsLogins).hasValue(2);
        assertThat(tmsCalls).hasValue(2);
    }

    @Test
    void serviceAccountForbidden_isNotRetried_norReauthenticated() throws IOException {
        String tmsUrl = tmsAnswering(403, "");
        TimesheetIntegrationImpl tms = tms(tmsUrl, null, umsIssuingTokens());

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AR service account is not permitted");
        assertThat(umsLogins).hasValue(1);
        assertThat(tmsCalls).hasValue(1);
    }

    @Test
    void umsLoginFailure_failsClosed_withoutCallingTms() throws IOException {
        ensureServer();
        server.createContext("/ums/auth/login", exchange -> {
            exchange.getRequestBody().readAllBytes();
            reply(exchange, Reply.of(401, ""));
        });
        String tmsUrl = tmsAnswering(200, TWO_TIMESHEETS);
        UmsServiceAuthProperties properties = new UmsServiceAuthProperties();
        properties.setEnabled(true);
        properties.setLoginUrl(baseUrl() + "/ums/auth/login");
        properties.setEmail("ar-service@paves.example");
        properties.setPassword("wrong-password");
        TimesheetIntegrationImpl tms = tms(tmsUrl, null, new UmsServiceTokenProvider(properties));

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOf(UmsAuthenticationException.class)
                .hasMessageNotContaining("wrong-password");
        assertThat(tmsCalls).hasValue(0);
    }

    // ================================================================ temporary failures: bounded retry

    @Test
    void tmsServerError_isRetriedABoundedNumberOfTimes_thenPropagated() throws IOException {
        TimesheetIntegrationImpl tms = tms(tmsAnswering(503, ""), "svc", null);

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOf(TmsServiceUnavailableException.class)
                .hasMessage("TMS service failed with status 503 after 3 attempts.");
        assertThat(tmsCalls).hasValue(3);
        assertThat(waits).hasSize(2);
    }

    @Test
    void tmsServerError_thatClears_succeedsOnRetry() throws IOException {
        TimesheetIntegrationImpl tms = tms(tmsAnswering((call, authorization) -> call == 1
                ? Reply.of(502, "") : Reply.of(200, TWO_TIMESHEETS)), "svc", null);

        assertThat(tms.getApprovedTimesheets(7L, START, END)).hasSize(2);
        assertThat(tmsCalls).hasValue(2);
    }

    @Test
    void rateLimited_honoursRetryAfter_andSucceeds() throws IOException {
        TimesheetIntegrationImpl tms = tms(tmsAnswering((call, authorization) -> call == 1
                ? new Reply(429, "", Map.of("Retry-After", "4"))
                : Reply.of(200, TWO_TIMESHEETS)), "svc", null);

        assertThat(tms.getApprovedTimesheets(7L, START, END)).hasSize(2);
        assertThat(waits).hasSize(1);
        assertThat(waits.get(0)).isGreaterThanOrEqualTo(Duration.ofSeconds(4));
    }

    @Test
    void rateLimited_withARetryAfterBeyondTheInCallCap_isLeftToTheRunLevelRetry() throws IOException {
        TimesheetIntegrationImpl tms = tms(tmsAnswering((call, authorization) ->
                new Reply(429, "", Map.of("Retry-After", "3600"))), "svc", null);

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOfSatisfying(TmsRateLimitedException.class, ex ->
                        assertThat(ex.getRetryAfter()).isEqualTo(Duration.ofHours(1)))
                .isInstanceOf(TmsServiceUnavailableException.class);
        assertThat(tmsCalls).hasValue(1);
        assertThat(waits).isEmpty();
    }

    @Test
    void rateLimited_throughout_endsAsRateLimited() throws IOException {
        TimesheetIntegrationImpl tms = tms(tmsAnswering(429, ""), "svc", null);

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOf(TmsRateLimitedException.class);
        assertThat(tmsCalls).hasValue(3);
    }

    @Test
    void refusedConnection_isRetriedABoundedNumberOfTimes() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        TimesheetIntegrationImpl tms = tms("http://127.0.0.1:" + closedPort + "/tms", "svc", null);

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOf(ResourceAccessException.class);
        assertThat(waits).hasSize(2);
    }

    @Test
    void clientErrors_areNotRetried() throws IOException {
        TimesheetIntegrationImpl tms = tms(tmsAnswering(404, ""), "svc", null);

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unexpected TMS response: 404");
        assertThat(tmsCalls).hasValue(1);
    }

    @Test
    void backoff_growsExponentially_withJitter_andIsCapped() {
        TimesheetIntegrationImpl tms = tms("http://tms.local", "svc", null);

        for (int i = 0; i < 50; i++) {
            assertThat(tms.backoff(1, null)).isBetween(Duration.ofMillis(500), Duration.ofSeconds(1));
            assertThat(tms.backoff(2, null)).isBetween(Duration.ofSeconds(1), Duration.ofSeconds(2));
            assertThat(tms.backoff(10, null)).isBetween(Duration.ofSeconds(5), Duration.ofSeconds(10));
        }
        assertThat(tms.backoff(1, Duration.ofSeconds(11))).isNull();
    }

    @Test
    void retryAfter_isReadAsSecondsOrHttpDate() {
        HttpHeaders seconds = new HttpHeaders();
        seconds.set(HttpHeaders.RETRY_AFTER, "120");
        HttpHeaders date = new HttpHeaders();
        date.set(HttpHeaders.RETRY_AFTER, DateTimeFormatter.RFC_1123_DATE_TIME
                .format(ZonedDateTime.now().plusMinutes(5)));
        HttpHeaders garbage = new HttpHeaders();
        garbage.set(HttpHeaders.RETRY_AFTER, "soon");

        assertThat(TimesheetIntegrationImpl.parseRetryAfter(seconds)).isEqualTo(Duration.ofSeconds(120));
        assertThat(TimesheetIntegrationImpl.parseRetryAfter(date))
                .isBetween(Duration.ofMinutes(4), Duration.ofMinutes(5));
        assertThat(TimesheetIntegrationImpl.parseRetryAfter(garbage)).isNull();
        assertThat(TimesheetIntegrationImpl.parseRetryAfter(new HttpHeaders())).isNull();
    }

    // G-31: a TMS 400 body can echo employee/timesheet details; it becomes the
    // run's stored failure reason, so it is not copied into the message.
    @Test
    void tmsBadRequest_bodyIsNotCopiedIntoTheFailureMessage() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(400,
                "{\"error\":\"invalid employee Asha Rao asha.rao@paves.example\"}"), "svc");

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TMS rejected the request (status 400")
                .hasMessageNotContaining("Asha")
                .hasMessageNotContaining("@paves.example");
    }

    @Test
    void malformedTmsResponse_isPropagatedNotTreatedAsNoTimesheets() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200, "{\"timesheets\": [ {"), "svc");

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOf(RestClientException.class);
    }

    @Test
    void responseWithoutTimesheets_isAnEmptyList() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200, "{\"projectId\":7}"), "svc");

        assertThat(tms.getApprovedTimesheets(7L, START, END)).isEmpty();
    }

    @Test
    void explicitlyEmptyTimesheetList_isAnEmptyList() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200,
                "{\"projectId\":7,\"billingPeriodStart\":\"2026-10-05\",\"billingPeriodEnd\":\"2026-10-11\","
                        + "\"timesheets\":[]}"), "svc");

        assertThat(tms.getApprovedTimesheets(7L, START, END)).isEmpty();
    }

    @Test
    void emptyResponseBody_isNotTreatedAsNoTimesheets() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200, ""), "svc");

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOf(TmsInvalidResponseException.class)
                .hasMessageContaining("empty response body");
    }

    // ================================================================ response verification

    @Test
    void timesheetsOfAnotherProject_areRejected() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200,
                TWO_TIMESHEETS.replace("\"projectId\":7", "\"projectId\":8")), "svc");

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOf(TmsInvalidResponseException.class)
                .hasMessageContaining("project 8 for a request for project 7");
    }

    @Test
    void timesheetsWithoutAProject_areRejected() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200,
                TWO_TIMESHEETS.replace("\"projectId\":7,", "")), "svc");

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOf(TmsInvalidResponseException.class)
                .hasMessageContaining("does not identify its project");
    }

    @Test
    void answerForAnotherPeriod_isRejected() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200,
                TWO_TIMESHEETS.replace("\"billingPeriodEnd\":\"2026-10-11\"", "\"billingPeriodEnd\":\"2026-10-31\"")),
                "svc");

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOf(TmsInvalidResponseException.class)
                .hasMessageContaining("instead of the requested 2026-10-05..2026-10-11");
    }

    @Test
    void timesheetDatedOutsideThePeriod_rejectsTheWholeResponse() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200,
                TWO_TIMESHEETS.replace("\"workDate\":\"2026-10-07\"", "\"workDate\":\"2026-10-12\"")), "svc");

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOf(TmsInvalidResponseException.class)
                .hasMessageContaining("Timesheet [TS-2] is dated 2026-10-12")
                .hasMessageNotContaining("Ravi");
    }

    @Test
    void timesheetWithoutAWorkDate_isRejected() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200,
                TWO_TIMESHEETS.replace("\"workDate\":\"2026-10-07\",", "")), "svc");

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOf(TmsInvalidResponseException.class)
                .hasMessageContaining("Timesheet [TS-2] has no work date");
    }

    // ================================================================ billable flag: billable / isBillable

    private static final LocalDate NOV_START = LocalDate.of(2026, 11, 1);
    private static final LocalDate NOV_END = LocalDate.of(2026, 11, 10);

    /**
     * Shape of the actual TMS billing-endpoint response for project 37,
     * 2026-11-01..2026-11-10: one record per timesheet with billable entries for
     * the project, {@code hours} = sum of those billable entries, and no
     * billable / isBillable / timesheetEntryId / rate. Ordered by workDate,
     * then user, as the TMS query does.
     */
    private static final String PROJECT_37_NOV = """
            {"projectId":37,"billingPeriodStart":"2026-11-01","billingPeriodEnd":"2026-11-10","timesheets":[
              {"timesheetId":76,"resourceId":12,"resourceName":"Employee","workDate":"2026-11-02","hours":9,
               "role":"Developer","approvalStatus":"APPROVED"},
              {"timesheetId":77,"resourceId":12,"resourceName":"Employee","workDate":"2026-11-03","hours":9,
               "role":"Developer","approvalStatus":"APPROVED"},
              {"timesheetId":78,"resourceId":12,"resourceName":"Employee","workDate":"2026-11-04","hours":9,
               "role":"Developer","approvalStatus":"APPROVED"},
              {"timesheetId":80,"resourceId":12,"resourceName":"Employee","workDate":"2026-11-05","hours":9,
               "role":"Developer","approvalStatus":"APPROVED"},
              {"timesheetId":79,"resourceId":12,"resourceName":"Employee","workDate":"2026-11-06","hours":9,
               "role":"Developer","approvalStatus":"APPROVED"}]}
            """;

    private final BillingSnapshotItemRepository claimedItems = mock(BillingSnapshotItemRepository.class);
    private final BillingAcquisitionValidator validator = new BillingAcquisitionValidator(claimedItems);

    /** The billing endpoint's records as the Builder would receive them: rate applied, then validated. */
    private ValidationResultDto acquireAndValidate(String tmsResponse) throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200, tmsResponse), "svc");
        List<TimesheetDto> timesheets = tms.getApprovedTimesheets(37L, NOV_START, NOV_END);
        // The rate never comes from TMS; the strategy sets it from the rate card.
        timesheets.forEach(t -> t.setHourlyRate(new BigDecimal("1000")));
        return validator.validate(BillingAcquisitionResultDto.builder()
                .billingConfigurationId(UUID.randomUUID()).timesheets(timesheets).build());
    }

    /** {@link #PROJECT_37_NOV} with {@code from} replaced by {@code to} inside timesheet {@code id}'s record only. */
    private static String withChangeIn(int id, String from, String to) {
        String marker = "{\"timesheetId\":" + id + ",";
        int recordStart = PROJECT_37_NOV.indexOf(marker);
        int recordEnd = PROJECT_37_NOV.indexOf('}', recordStart);
        String record = PROJECT_37_NOV.substring(recordStart, recordEnd);
        assertThat(record).contains(from);
        return PROJECT_37_NOV.substring(0, recordStart) + record.replace(from, to)
                + PROJECT_37_NOV.substring(recordEnd);
    }

    @Test
    void project37Response_withoutAnyBillableProperty_allFiveTimesheetsAreBillable_45Hours() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200, PROJECT_37_NOV), "svc");

        List<TimesheetDto> timesheets = tms.getApprovedTimesheets(37L, NOV_START, NOV_END);

        assertThat(timesheets).extracting(TimesheetDto::getSourceReferenceId)
                .containsExactly("76", "77", "78", "80", "79");
        assertThat(timesheets).allSatisfy(t -> {
            assertThat(t.isBillable()).isTrue();
            assertThat(t.isApproved()).isTrue();
            assertThat(t.getHours()).isEqualByComparingTo("9");
            assertThat(t.getHourlyRate()).isNull();
        });
        assertThat(timesheets.stream().map(TimesheetDto::getHours).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("45");
    }

    @Test
    void project37Response_passesTheWholeValidation() throws IOException {
        ValidationResultDto outcome = acquireAndValidate(PROJECT_37_NOV);

        assertThat(outcome.isSuccess()).as(outcome.getValidationMessage()).isTrue();
        assertThat(outcome.getAcquisitionResult().getTimesheets()).hasSize(5)
                .allSatisfy(t -> assertThat(t.isBillable()).isTrue());
    }

    @Test
    void project37Response_aNonApprovedTimesheet_isStillRejected() throws IOException {
        ValidationResultDto outcome = acquireAndValidate(
                withChangeIn(77, "\"approvalStatus\":\"APPROVED\"", "\"approvalStatus\":\"SUBMITTED\""));

        assertThat(outcome.isSuccess()).isFalse();
        assertThat(outcome.getValidationMessage()).isEqualTo("Timesheet [77] is not approved.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"hours\":0", "\"hours\":-1", "\"hours\":null"})
    void project37Response_zeroNegativeOrMissingHours_areStillRejected(String hours) throws IOException {
        ValidationResultDto outcome = acquireAndValidate(withChangeIn(79, "\"hours\":9", hours));

        assertThat(outcome.isSuccess()).isFalse();
        assertThat(outcome.getValidationMessage()).isEqualTo("Timesheet [79] has invalid hours.");
    }

    @Test
    void project37Response_aDuplicateTimesheetId_isStillRejected() throws IOException {
        ValidationResultDto outcome = acquireAndValidate(withChangeIn(80, "\"timesheetId\":80", "\"timesheetId\":78"));

        assertThat(outcome.isSuccess()).isFalse();
        assertThat(outcome.getValidationMessage()).isEqualTo("Timesheet [78] is a duplicate source reference.");
    }

    @Test
    void project37Response_aTimesheetAlreadyClaimedByAnotherSnapshot_isStillRejected() throws IOException {
        when(claimedItems.findClaimedSourceReferenceIds(any(), any(), any(), any())).thenReturn(List.of("78"));

        ValidationResultDto outcome = acquireAndValidate(PROJECT_37_NOV);

        assertThat(outcome.isSuccess()).isFalse();
        assertThat(outcome.getValidationMessage())
                .isEqualTo("Timesheet(s) [78] are already included in another Billing Snapshot and cannot be billed again.");
    }

    @Test
    void project37Response_aTimesheetWithoutAnId_isStillRejected() throws IOException {
        ValidationResultDto outcome = acquireAndValidate(withChangeIn(80, "\"timesheetId\":80,", "\"timesheetId\":null,"));

        assertThat(outcome.isSuccess()).isFalse();
        assertThat(outcome.getValidationMessage()).contains("has no TMS timesheet id");
    }

    @Test
    void project37Response_anExplicitIsBillableFalse_isStillRejected() throws IOException {
        ValidationResultDto outcome = acquireAndValidate(
                withChangeIn(78, "\"approvalStatus\":\"APPROVED\"", "\"approvalStatus\":\"APPROVED\",\"isBillable\":false"));

        assertThat(outcome.isSuccess()).isFalse();
        assertThat(outcome.getValidationMessage()).isEqualTo("Timesheet [78] is not billable.");
    }

    /** One approved entry for project 7 / TS-1 whose billable flag(s) are {@code flags} (JSON members or ""). */
    private static String oneTimesheetWith(String flags) {
        return """
                {"projectId":7,"billingPeriodStart":"2026-10-05","billingPeriodEnd":"2026-10-11","timesheets":[
                  {"timesheetId":"TS-1","resourceId":1,"resourceName":"Asha","workDate":"2026-10-06","hours":8,
                   "role":"Developer","approvalStatus":"APPROVED"%s}]}
                """.formatted(flags.isEmpty() ? "" : "," + flags);
    }

    private TimesheetDto acquireOne(String flags) throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200, oneTimesheetWith(flags)), "svc");
        List<TimesheetDto> timesheets = tms.getApprovedTimesheets(7L, START, END);
        assertThat(timesheets).hasSize(1);
        return timesheets.get(0);
    }

    @ParameterizedTest(name = "[{index}] {0} -> billable={1}")
    @CsvSource(delimiter = '|', value = {
            "\"isBillable\":true                       | true",
            "\"isBillable\":false                      | false",
            "\"billable\":true                         | true",
            "\"billable\":false                        | false",
            "\"billable\":true,\"isBillable\":true     | true",
            "\"isBillable\":true,\"billable\":true     | true",
            "\"billable\":false,\"isBillable\":false   | false",
            "\"billable\":true,\"isBillable\":null     | true",
            "\"billable\":null,\"isBillable\":true     | true",
            "\"billable\":null,\"isBillable\":null     | true",
            "\"isBillable\":\"true\"                   | true",
    })
    void billableFlag_fromTheTmsJson(String flags, boolean billable) throws IOException {
        assertThat(acquireOne(flags.trim()).isBillable()).isEqualTo(billable);
    }

    @Test
    void noBillableProperty_isBillableByTheBillingEndpointContract() throws IOException {
        assertThat(acquireOne("").isBillable()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"billable\":true,\"isBillable\":false",
            "\"isBillable\":false,\"billable\":true",
            "\"billable\":false,\"isBillable\":true",
            "\"isBillable\":true,\"billable\":false"})
    void conflictingBillableFlags_rejectTheWholeResponse(String flags) throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200, oneTimesheetWith(flags)), "svc");

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOf(TmsInvalidResponseException.class)
                .hasMessageContaining("Timesheet [TS-1] has conflicting billable flags")
                .hasMessageNotContaining("Asha");
    }

    @Test
    void unreadableIsBillable_isAMalformedResponse_neverBillable() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(
                tmsAnswering(200, oneTimesheetWith("\"isBillable\":\"Y\"")), "svc");

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOf(RestClientException.class);
    }

    @ParameterizedTest(name = "[{index}] billable={0}, isBillable={1} -> {2}")
    @CsvSource(nullValues = "null", value = {
            "true,  null,  TRUE",
            "false, null,  FALSE",
            "null,  true,  TRUE",
            "null,  false, FALSE",
            "true,  true,  TRUE",
            "false, false, FALSE",
            "null,  null,  BY_BILLING_ENDPOINT_CONTRACT",
    })
    void resolveBillable_table(Boolean billable, Boolean isBillable, TimesheetIntegrationImpl.BillableFlag expected) {
        assertThat(TimesheetIntegrationImpl.resolveBillable("TS-9", billable, isBillable)).isEqualTo(expected);
    }

    @Test
    void resolveBillable_conflict_throws() {
        assertThatThrownBy(() -> TimesheetIntegrationImpl.resolveBillable("TS-9", true, false))
                .isInstanceOf(TmsInvalidResponseException.class)
                .hasMessage("Timesheet [TS-9] has conflicting billable flags (billable=true, isBillable=false); "
                        + "nothing was billed.");
        assertThatThrownBy(() -> TimesheetIntegrationImpl.resolveBillable("TS-9", false, true))
                .isInstanceOf(TmsInvalidResponseException.class);
    }

    private static void callerSends(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", authorization);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    // ================================================================ timeouts (Phase 12 regression)

    @Test
    void tmsThatNeverAnswers_timesOutInsteadOfBlockingForever_andIsNotRetriedInCall() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor());
        server.createContext("/tms/api/timesheets/billing", exchange -> {
            tmsCalls.incrementAndGet();
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/tms",
                Duration.ofSeconds(2), Duration.ofMillis(300), "svc", null, RETRY, waits::add);

        long startedAt = System.nanoTime();
        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L, START, END))
                .isInstanceOf(ResourceAccessException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(3));
        assertThat(tmsCalls).hasValue(1);
        assertThat(waits).isEmpty();
    }

    @Test
    void defaultTimeouts_areBounded() {
        assertThat(TimesheetIntegrationImpl.DEFAULT_CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(10));
        assertThat(TimesheetIntegrationImpl.DEFAULT_READ_TIMEOUT).isEqualTo(Duration.ofSeconds(60));
        assertThat(TimesheetIntegrationImpl.DEFAULT_RETRY.maxAttempts()).isEqualTo(3);
    }
}
