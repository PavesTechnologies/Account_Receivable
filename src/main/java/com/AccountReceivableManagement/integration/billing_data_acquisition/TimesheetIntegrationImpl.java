package com.AccountReceivableManagement.integration.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.TimesheetDto;
import com.AccountReceivableManagement.integration.ums.UmsServiceTokenProvider;
import com.AccountReceivableManagement.security.ActorContext;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.ConnectException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Calls the real TMS billing-timesheets API. Returns operational data only
 * (resource, hours, source reference) — {@code hourlyRate} is intentionally
 * left unset here; TMS never knows commercial data, so
 * {@code TimeAndMaterialBillingStrategy} fills it in from the Billing
 * Configuration after this call returns.
 * <p>
 * Authentication:
 * <ul>
 *   <li>a user's request: the caller's own {@code Authorization} header is
 *       forwarded (as {@code TmsFeignConfig} does for the Feign clients);</li>
 *   <li>the system (automatic billing, {@link ActorContext#isSystem()}) or a
 *       thread without a request: AR's own service credential - a UMS
 *       service-account token from {@link UmsServiceTokenProvider} when that
 *       is enabled, otherwise the static {@code tms.api.service-token}. With
 *       neither, TMS is not called at all (fail closed). A UMS token TMS
 *       refuses (401) is dropped and replaced by one fresh login, once.</li>
 * </ul>
 * Temporary TMS failures - 429, 5xx, connection refused - are retried within
 * the call a bounded number of times with exponential backoff and jitter,
 * honouring {@code Retry-After}; a {@code Retry-After} longer than the
 * in-call backoff cap is left to automatic billing's own (run-level) retry.
 * Read timeouts are not retried here: a TMS that does not answer would
 * otherwise hold the scheduler thread for several read timeouts.
 * <p>
 * The response is only accepted when it can be verified as the requested
 * project and period - see {@link #verifyAndMap}.
 */
@Slf4j
@Component
public class TimesheetIntegrationImpl implements TimesheetIntegration {

    private static final String TIMESHEETS_BILLING_PATH = "/api/timesheets/billing";

    /** TMS {@code approvalStatus} value of an approved timesheet. */
    private static final String APPROVED_STATUS = "APPROVED";

    /** Defaults of {@code tms.api.connect-timeout} / {@code tms.api.read-timeout}. */
    static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(60);

    /** Defaults of {@code tms.api.retry.*}. */
    static final RetrySettings DEFAULT_RETRY = new RetrySettings(3, Duration.ofSeconds(1), Duration.ofSeconds(10));

    /** Attempts per call (first try included), and the backoff between them. */
    record RetrySettings(int maxAttempts, Duration initialBackoff, Duration maxBackoff) {
        RetrySettings {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("tms.api.retry.max-attempts must be at least 1.");
            }
            if (initialBackoff == null || initialBackoff.isNegative() || maxBackoff == null
                    || maxBackoff.compareTo(initialBackoff) < 0) {
                throw new IllegalArgumentException(
                        "tms.api.retry backoffs must be non-negative, with max-backoff >= initial-backoff.");
            }
        }
    }

    /** Waits between attempts; replaced in tests. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private enum CredentialSource { CALLER, UMS_SERVICE, STATIC_SERVICE }

    private record Credential(String authorizationHeader, CredentialSource source) {
    }

    private final RestClient restClient;
    private final String serviceToken;
    private final UmsServiceTokenProvider umsServiceTokenProvider;
    private final RetrySettings retry;
    private final Sleeper sleeper;

    public TimesheetIntegrationImpl(String tmsApiBaseUrl) {
        this(tmsApiBaseUrl, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT, null);
    }

    public TimesheetIntegrationImpl(String tmsApiBaseUrl, Duration connectTimeout, Duration readTimeout) {
        this(tmsApiBaseUrl, connectTimeout, readTimeout, null);
    }

    public TimesheetIntegrationImpl(String tmsApiBaseUrl, String serviceToken) {
        this(tmsApiBaseUrl, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT, serviceToken);
    }

    public TimesheetIntegrationImpl(String tmsApiBaseUrl, Duration connectTimeout, Duration readTimeout,
            String serviceToken) {
        this(tmsApiBaseUrl, connectTimeout, readTimeout, serviceToken, null, DEFAULT_RETRY, Thread::sleep);
    }

    /**
     * Bounded connect and read timeouts: without them a TMS that accepts the
     * connection but never answers would block the caller - a user request,
     * or the automatic billing scheduler thread past its run's claim - for
     * good. A timeout surfaces as {@code ResourceAccessException}, which
     * automatic billing retries.
     */
    @Autowired
    public TimesheetIntegrationImpl(@Value("${tms.api.base-url}") String tmsApiBaseUrl,
            @Value("${tms.api.connect-timeout:PT10S}") Duration connectTimeout,
            @Value("${tms.api.read-timeout:PT60S}") Duration readTimeout,
            @Value("${tms.api.service-token:${TMS_SERVICE_TOKEN:}}") String serviceToken,
            UmsServiceTokenProvider umsServiceTokenProvider,
            @Value("${tms.api.retry.max-attempts:3}") int retryMaxAttempts,
            @Value("${tms.api.retry.initial-backoff:PT1S}") Duration retryInitialBackoff,
            @Value("${tms.api.retry.max-backoff:PT10S}") Duration retryMaxBackoff) {
        this(tmsApiBaseUrl, connectTimeout, readTimeout, serviceToken, umsServiceTokenProvider,
                new RetrySettings(retryMaxAttempts, retryInitialBackoff, retryMaxBackoff), Thread::sleep);
        logBackgroundCredential();
    }

    TimesheetIntegrationImpl(String tmsApiBaseUrl, Duration connectTimeout, Duration readTimeout,
            String serviceToken, UmsServiceTokenProvider umsServiceTokenProvider, RetrySettings retry,
            Sleeper sleeper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) connectTimeout.toMillis());
        requestFactory.setReadTimeout((int) readTimeout.toMillis());
        this.restClient = RestClient.builder().baseUrl(tmsApiBaseUrl).requestFactory(requestFactory).build();
        this.serviceToken = serviceToken != null && !serviceToken.isBlank() ? serviceToken.trim() : null;
        this.umsServiceTokenProvider = umsServiceTokenProvider;
        this.retry = retry;
        this.sleeper = sleeper;
    }

    private void logBackgroundCredential() {
        if (umsEnabled()) {
            if (serviceToken != null) {
                log.warn("[TmsIntegration] both UMS service authentication and TMS_SERVICE_TOKEN are configured; "
                        + "background TMS calls use the UMS service token. Remove TMS_SERVICE_TOKEN.");
            } else {
                log.info("[TmsIntegration] background TMS calls authenticate with the UMS service account.");
            }
        } else if (serviceToken != null) {
            log.info("[TmsIntegration] background TMS calls authenticate with the static TMS_SERVICE_TOKEN.");
        } else {
            log.warn("[TmsIntegration] no service credential configured: background (automatic billing) "
                    + "Time & Material acquisition will fail without calling TMS. Configure "
                    + "AR_UMS_SERVICE_AUTH_ENABLED with UMS_LOGIN_URL / UMS_SERVICE_EMAIL / UMS_SERVICE_PASSWORD.");
        }
    }

    @Override
    public List<TimesheetDto> getApprovedTimesheets(Long projectId, LocalDate billingPeriodStart, LocalDate billingPeriodEnd) {
        Credential credential = resolveCredential();
        TmsTimesheetBillingResponse response = fetch(projectId, billingPeriodStart, billingPeriodEnd, credential);
        return verifyAndMap(response, projectId, billingPeriodStart, billingPeriodEnd);
    }

    // ================================================================ authentication

    /**
     * The user's own JWT for a user's request; AR's service credential for
     * the system and for threads without a request. Never no credential.
     */
    private Credential resolveCredential() {
        if (!ActorContext.isSystem()) {
            String callerAuthorization = currentAuthorizationHeader();
            if (callerAuthorization != null) {
                return new Credential(callerAuthorization, CredentialSource.CALLER);
            }
        }
        if (umsEnabled()) {
            return new Credential(bearer(umsServiceTokenProvider.getAccessToken()), CredentialSource.UMS_SERVICE);
        }
        if (serviceToken != null) {
            return new Credential(bearer(serviceToken), CredentialSource.STATIC_SERVICE);
        }
        throw new IllegalStateException("TMS was not called: background TMS access requires service-to-service "
                + "authentication and none is configured (enable AR_UMS_SERVICE_AUTH_ENABLED with UMS_LOGIN_URL, "
                + "UMS_SERVICE_EMAIL and UMS_SERVICE_PASSWORD).");
    }

    private boolean umsEnabled() {
        return umsServiceTokenProvider != null && umsServiceTokenProvider.isEnabled();
    }

    private static String bearer(String token) {
        return token.startsWith("Bearer ") ? token : "Bearer " + token;
    }

    /** The incoming request's Authorization header; {@code null} outside a request (scheduler threads). */
    private static String currentAuthorizationHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        String header = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && !header.isBlank() ? header : null;
    }

    // ================================================================ HTTP call with bounded retry

    private TmsTimesheetBillingResponse fetch(Long projectId, LocalDate billingPeriodStart,
            LocalDate billingPeriodEnd, Credential initialCredential) {
        Credential credential = initialCredential;
        boolean reauthenticated = false;
        int attempt = 1;
        while (true) {
            try {
                return call(projectId, billingPeriodStart, billingPeriodEnd, credential);
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                Metrics.counter("ar.tms.request.failures", "status", String.valueOf(status)).increment();

                if (status == 401 && credential.source() == CredentialSource.UMS_SERVICE && !reauthenticated) {
                    // The cached token may have been revoked or expired early: one fresh login, once.
                    log.warn("[TmsIntegration] TMS refused the UMS service token (401); re-authenticating once.");
                    umsServiceTokenProvider.invalidate(tokenOf(credential));
                    credential = new Credential(bearer(umsServiceTokenProvider.getAccessToken()),
                            CredentialSource.UMS_SERVICE);
                    reauthenticated = true;
                    continue;
                }

                if (status == 429 || status >= 500) {
                    Duration retryAfter = parseRetryAfter(ex.getResponseHeaders());
                    Duration wait = attempt < retry.maxAttempts() ? backoff(attempt, retryAfter) : null;
                    if (wait != null) {
                        log.warn("[TmsIntegration] TMS answered {} (attempt {}/{}); retrying in {} ms.",
                                status, attempt, retry.maxAttempts(), wait.toMillis());
                        pause(wait);
                        attempt++;
                        continue;
                    }
                    if (status == 429) {
                        throw new TmsRateLimitedException("TMS rate limit reached (status 429) after " + attempt
                                + " attempt(s)" + (retryAfter != null
                                        ? "; TMS asked to retry after " + retryAfter.toSeconds() + "s." : "."),
                                retryAfter);
                    }
                    throw new TmsServiceUnavailableException("TMS service failed with status " + status
                            + (attempt > 1 ? " after " + attempt + " attempts." : "."));
                }
                throw translateTmsError(ex, credential.source());
            } catch (ResourceAccessException ex) {
                // Unreachable or timed out. Only a refused connection is retried here - it fails
                // fast; a read timeout is left to automatic billing's run-level retry.
                Metrics.counter("ar.tms.request.failures", "status", "io").increment();
                if (isConnectionRefused(ex) && attempt < retry.maxAttempts()) {
                    Duration wait = backoff(attempt, null);
                    log.warn("[TmsIntegration] TMS unreachable (attempt {}/{}); retrying in {} ms.",
                            attempt, retry.maxAttempts(), wait.toMillis());
                    pause(wait);
                    attempt++;
                    continue;
                }
                throw ex;
            } catch (RestClientException ex) {
                // Unreadable (malformed) response: never treated as "no timesheets".
                Metrics.counter("ar.tms.request.failures", "status", "malformed").increment();
                throw ex;
            }
        }
    }

    private TmsTimesheetBillingResponse call(Long projectId, LocalDate billingPeriodStart,
            LocalDate billingPeriodEnd, Credential credential) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path(TIMESHEETS_BILLING_PATH)
                        .queryParam("projectId", projectId)
                        .queryParam("billingPeriodStart", billingPeriodStart)
                        .queryParam("billingPeriodEnd", billingPeriodEnd)
                        .build())
                .headers(headers -> headers.set(HttpHeaders.AUTHORIZATION, credential.authorizationHeader()))
                .retrieve()
                .body(TmsTimesheetBillingResponse.class);
    }

    private static String tokenOf(Credential credential) {
        return credential.authorizationHeader().substring("Bearer ".length());
    }

    /**
     * Exponential backoff with jitter for the {@code attempt}-th failure:
     * half the capped exponential delay plus a random share of the other
     * half. A {@code Retry-After} is honoured as a minimum; one beyond the
     * cap returns {@code null} - not waited for on this thread.
     */
    Duration backoff(int attempt, Duration retryAfter) {
        if (retryAfter != null && retryAfter.compareTo(retry.maxBackoff()) > 0) {
            return null;
        }
        long initial = retry.initialBackoff().toMillis();
        long cap = retry.maxBackoff().toMillis();
        double exponential = initial * Math.pow(2, Math.max(0, attempt - 1));
        long capped = exponential >= cap ? cap : (long) exponential;
        long half = capped / 2;
        long jittered = half + (capped - half > 0 ? ThreadLocalRandom.current().nextLong(capped - half + 1) : 0);
        Duration wait = Duration.ofMillis(jittered);
        return retryAfter != null && retryAfter.compareTo(wait) > 0 ? retryAfter : wait;
    }

    private void pause(Duration wait) {
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TmsServiceUnavailableException("Interrupted while waiting to retry the TMS call.");
        }
    }

    /** {@code Retry-After} as delta-seconds or an HTTP date; {@code null} when absent or unreadable. */
    static Duration parseRetryAfter(HttpHeaders headers) {
        String value = headers != null ? headers.getFirst(HttpHeaders.RETRY_AFTER) : null;
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            long seconds = Long.parseLong(trimmed);
            return seconds >= 0 ? Duration.ofSeconds(seconds) : null;
        } catch (NumberFormatException notSeconds) {
            try {
                Duration until = Duration.between(ZonedDateTime.now(),
                        ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME));
                return until.isNegative() ? Duration.ZERO : until;
            } catch (DateTimeParseException unreadable) {
                return null;
            }
        }
    }

    private static boolean isConnectionRefused(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof ConnectException) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ response verification

    /**
     * Accepts the response only as the requested project and period: TMS's
     * echoed {@code projectId} and period must match the request, a non-empty
     * response must name its project, and every timesheet must carry a work
     * date inside the period. A violation rejects the whole response - no
     * entry is silently dropped - so data of another project or period can
     * never reach a snapshot. An empty body is not read as "no timesheets".
     */
    List<TimesheetDto> verifyAndMap(TmsTimesheetBillingResponse response, Long projectId,
            LocalDate billingPeriodStart, LocalDate billingPeriodEnd) {
        if (response == null) {
            throw new TmsInvalidResponseException("TMS returned an empty response body for project " + projectId
                    + "; it is not treated as 'no timesheets'.");
        }
        List<TmsTimesheetEntry> entries = response.getTimesheets() != null ? response.getTimesheets() : List.of();

        if (response.getProjectId() != null && !response.getProjectId().equals(projectId)) {
            throw new TmsInvalidResponseException("TMS returned timesheets of project " + response.getProjectId()
                    + " for a request for project " + projectId + "; nothing was billed.");
        }
        if (response.getProjectId() == null && !entries.isEmpty()) {
            throw new TmsInvalidResponseException("TMS response does not identify its project, so its timesheets "
                    + "cannot be verified as belonging to project " + projectId + ".");
        }
        if ((response.getBillingPeriodStart() != null && !response.getBillingPeriodStart().equals(billingPeriodStart))
                || (response.getBillingPeriodEnd() != null && !response.getBillingPeriodEnd().equals(billingPeriodEnd))) {
            throw new TmsInvalidResponseException("TMS answered for billing period " + response.getBillingPeriodStart()
                    + ".." + response.getBillingPeriodEnd() + " instead of the requested " + billingPeriodStart
                    + ".." + billingPeriodEnd + ".");
        }

        List<TimesheetDto> timesheets = new ArrayList<>(entries.size());
        for (TmsTimesheetEntry entry : entries) {
            if (entry == null) {
                throw new TmsInvalidResponseException("TMS response for project " + projectId
                        + " contains an empty timesheet entry.");
            }
            LocalDate workDate = entry.getWorkDate();
            if (workDate == null) {
                throw new TmsInvalidResponseException("Timesheet [" + Objects.toString(entry.getTimesheetId(), "?")
                        + "] has no work date, so it cannot be verified as part of the billing period.");
            }
            if (workDate.isBefore(billingPeriodStart) || workDate.isAfter(billingPeriodEnd)) {
                throw new TmsInvalidResponseException("Timesheet [" + Objects.toString(entry.getTimesheetId(), "?")
                        + "] is dated " + workDate + ", outside the requested billing period " + billingPeriodStart
                        + ".." + billingPeriodEnd + ".");
            }
            timesheets.add(toTimesheetDto(entry));
        }
        return timesheets;
    }

    /**
     * Maps one TMS entry as sent - approval and billability are taken from
     * TMS's own {@code approvalStatus} and {@code billable} values, never
     * assumed, so {@code BillingAcquisitionValidator} decides eligibility
     * from the real source data.
     */
    TimesheetDto toTimesheetDto(TmsTimesheetEntry entry) {
        return TimesheetDto.builder()
                .resourceId(entry.getResourceId())
                .resourceName(entry.getResourceName())
                .sourceReferenceId(entry.getTimesheetId())
                .workDate(entry.getWorkDate())
                .hours(entry.getHours())
                .role(entry.getRole())
                .approvalStatus(entry.getApprovalStatus())
                .approved(entry.getApprovalStatus() != null
                        && APPROVED_STATUS.equalsIgnoreCase(entry.getApprovalStatus().trim()))
                .billable(Boolean.TRUE.equals(entry.getBillable()))
                .build();
    }

    private RuntimeException translateTmsError(RestClientResponseException ex, CredentialSource source) {
        HttpStatusCode status = ex.getStatusCode();

        if (status.value() == 400) {
            // The response body can echo employee and timesheet details; it becomes
            // the run's stored failure reason, so only its size is kept (G-31).
            return new IllegalArgumentException("TMS rejected the request (status 400, "
                    + ex.getResponseBodyAsString().length() + "-character response body not recorded).");
        }
        if (status.value() == 401) {
            return switch (source) {
                case UMS_SERVICE -> new IllegalStateException("TMS authentication failed: TMS refused the UMS "
                        + "service token even after re-authenticating - check that TMS accepts tokens of the "
                        + "AR service account.");
                case STATIC_SERVICE -> new IllegalStateException(
                        "TMS authentication failed: service-to-service token is invalid or expired.");
                case CALLER -> new IllegalStateException("TMS authentication failed: the JWT is invalid or expired.");
            };
        }
        if (status.value() == 403) {
            return new IllegalStateException(source == CredentialSource.CALLER
                    ? "TMS authorization failed: insufficient permissions to read timesheets."
                    : "TMS authorization failed: the AR service account is not permitted to read billing "
                            + "timesheets.");
        }
        return new IllegalStateException("Unexpected TMS response: " + status.value());
    }
}
