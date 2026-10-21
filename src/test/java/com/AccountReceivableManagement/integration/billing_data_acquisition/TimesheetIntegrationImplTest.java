package com.AccountReceivableManagement.integration.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.TimesheetDto;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TMS-to-AR mapping: approval and billability come from TMS's own values,
 * never forced, so the validator sees the real source data. The HTTP tests
 * run against a local stub server: the exact period and the Authorization
 * forwarding (or its absence on a scheduler thread) are checked on the wire.
 */
class TimesheetIntegrationImplTest {

    private final TimesheetIntegrationImpl integration = new TimesheetIntegrationImpl("http://tms.local");

    private HttpServer server;
    private final AtomicReference<String> receivedQuery = new AtomicReference<>();
    private final AtomicReference<String> receivedAuthorization = new AtomicReference<>();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        if (server != null) {
            server.stop(0);
        }
    }

    /** Starts a TMS stub answering {@code /tms/api/timesheets/billing}; returns the base URL. */
    private String tmsAnswering(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tms/api/timesheets/billing", exchange -> {
            receivedQuery.set(exchange.getRequestURI().getQuery());
            receivedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/tms";
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
    void missingBillableOrApprovalValues_areNotTreatedAsEligible() {
        TimesheetDto dto = integration.toTimesheetDto(entry(null, null));

        assertThat(dto.isApproved()).isFalse();
        assertThat(dto.isBillable()).isFalse();
    }

    // ================================================================ HTTP call

    @Test
    void callsTmsForExactlyTheRequestedPeriod_andMapsTheResponseAsSent() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200, TWO_TIMESHEETS));

        List<TimesheetDto> timesheets = tms.getApprovedTimesheets(7L,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11));

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

        tms.getApprovedTimesheets(7L, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11));

        assertThat(receivedAuthorization.get()).isEqualTo("Bearer user-jwt");
    }

    @Test
    void onASchedulerThread_withoutServiceToken_callsWithoutAuthorization() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200, TWO_TIMESHEETS));

        List<TimesheetDto> timesheets = tms.getApprovedTimesheets(7L,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11));

        assertThat(receivedAuthorization.get()).isNull();
        assertThat(timesheets).hasSize(2);
    }

    @Test
    void onASchedulerThread_withServiceToken_sendsBearerAuthorization() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200, TWO_TIMESHEETS), "svc-token-xyz");

        List<TimesheetDto> timesheets = tms.getApprovedTimesheets(7L,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11));

        assertThat(receivedAuthorization.get()).isEqualTo("Bearer svc-token-xyz");
        assertThat(timesheets).hasSize(2);
    }

    @Test
    void onASchedulerThread_withoutServiceToken_tmsRefusal_namesTheMissingServiceAuthentication() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(401, ""));

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("service-to-service authentication");
    }

    @Test
    void onASchedulerThread_withServiceToken_tmsRefusal_reportsInvalidServiceToken() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(401, ""), "invalid-svc-token");

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TMS authentication failed: service-to-service token is invalid or expired.");
    }

    @Test
    void withACallerJwt_tmsRefusal_reportsTheInvalidJwt() throws IOException {
        callerSends("Bearer expired");
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(401, ""));

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11)))
                .hasMessage("TMS authentication failed: the JWT is invalid or expired.");
    }

    @Test
    void tmsServerError_isPropagated() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(503, ""));

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11)))
                .hasMessage("TMS service failed with status 503.");
    }

    // G-31: a TMS 400 body can echo employee/timesheet details; it becomes the
    // run's stored failure reason, so it is not copied into the message.
    @Test
    void tmsBadRequest_bodyIsNotCopiedIntoTheFailureMessage() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(400,
                "{\"error\":\"invalid employee Asha Rao asha.rao@paves.example\"}"));

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TMS rejected the request (status 400")
                .hasMessageNotContaining("Asha")
                .hasMessageNotContaining("@paves.example");
    }

    @Test
    void malformedTmsResponse_isPropagatedNotTreatedAsNoTimesheets() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200, "{\"timesheets\": [ {"));

        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11)))
                .isInstanceOf(RestClientException.class);
    }

    @Test
    void responseWithoutTimesheets_isAnEmptyList() throws IOException {
        TimesheetIntegrationImpl tms = new TimesheetIntegrationImpl(tmsAnswering(200, "{\"projectId\":7}"));

        assertThat(tms.getApprovedTimesheets(7L, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11)))
                .isEmpty();
    }

    private static void callerSends(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", authorization);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    // ================================================================ timeouts (Phase 12 regression)

    @Test
    void tmsThatNeverAnswers_timesOutInsteadOfBlockingForever() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor());
        server.createContext("/tms/api/timesheets/billing", exchange -> {
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
                java.time.Duration.ofSeconds(2), java.time.Duration.ofMillis(300));

        long startedAt = System.nanoTime();
        assertThatThrownBy(() -> tms.getApprovedTimesheets(7L,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11)))
                .isInstanceOf(org.springframework.web.client.ResourceAccessException.class);
        assertThat(java.time.Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(java.time.Duration.ofSeconds(3));
    }

    @Test
    void defaultTimeouts_areBounded() {
        assertThat(TimesheetIntegrationImpl.DEFAULT_CONNECT_TIMEOUT).isEqualTo(java.time.Duration.ofSeconds(10));
        assertThat(TimesheetIntegrationImpl.DEFAULT_READ_TIMEOUT).isEqualTo(java.time.Duration.ofSeconds(60));
    }
}
