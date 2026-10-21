package com.AccountReceivableManagement.integration.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.TimesheetDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import io.micrometer.core.instrument.Metrics;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Calls the real TMS billing-timesheets API. Returns operational data only
 * (resource, hours, source reference) — {@code hourlyRate} is intentionally
 * left unset here; TMS never knows commercial data, so
 * {@code TimeAndMaterialBillingStrategy} fills it in from the Billing
 * Configuration after this call returns.
 * <p>
 * Authentication: the caller's own {@code Authorization} header is forwarded
 * (as {@code TmsFeignConfig} does for the Feign clients). Outside an HTTP
 * request - the automatic billing scheduler - there is none, and no
 * service-to-service credential exists yet, so TMS is called without one;
 * if TMS then refuses it, the error names that missing dependency rather
 * than an expired user JWT.
 */
@Component
public class TimesheetIntegrationImpl implements TimesheetIntegration {

    private static final String TIMESHEETS_BILLING_PATH = "/api/timesheets/billing";

    /** TMS {@code approvalStatus} value of an approved timesheet. */
    private static final String APPROVED_STATUS = "APPROVED";

    /** Defaults of {@code tms.api.connect-timeout} / {@code tms.api.read-timeout}. */
    static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final String serviceToken;

    public TimesheetIntegrationImpl(String tmsApiBaseUrl) {
        this(tmsApiBaseUrl, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT, null);
    }

    public TimesheetIntegrationImpl(String tmsApiBaseUrl, Duration connectTimeout, Duration readTimeout) {
        this(tmsApiBaseUrl, connectTimeout, readTimeout, null);
    }

    public TimesheetIntegrationImpl(String tmsApiBaseUrl, String serviceToken) {
        this(tmsApiBaseUrl, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT, serviceToken);
    }

    /**
     * Bounded connect and read timeouts: without them a TMS that accepts the
     * connection but never answers would block the caller - a user request,
     * or the automatic billing scheduler thread past its run's claim - for
     * good. A timeout surfaces as {@code ResourceAccessException}, which
     * automatic billing retries.
     * <p>
     * Outside interactive user sessions, {@code serviceToken} provides the
     * configured service-to-service credential.
     */
    @Autowired
    public TimesheetIntegrationImpl(@Value("${tms.api.base-url}") String tmsApiBaseUrl,
            @Value("${tms.api.connect-timeout:PT10S}") Duration connectTimeout,
            @Value("${tms.api.read-timeout:PT60S}") Duration readTimeout,
            @Value("${tms.api.service-token:${TMS_SERVICE_TOKEN:}}") String serviceToken) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) connectTimeout.toMillis());
        requestFactory.setReadTimeout((int) readTimeout.toMillis());
        this.restClient = RestClient.builder().baseUrl(tmsApiBaseUrl).requestFactory(requestFactory).build();
        this.serviceToken = serviceToken != null && !serviceToken.isBlank() ? serviceToken.trim() : null;
    }

    @Override
    public List<TimesheetDto> getApprovedTimesheets(Long projectId, LocalDate billingPeriodStart, LocalDate billingPeriodEnd) {
        String callerAuth = currentAuthorizationHeader();
        boolean callerAuthSent = callerAuth != null;
        boolean serviceTokenSent = false;
        String resolvedAuthorization = callerAuth;

        if (resolvedAuthorization == null && serviceToken != null) {
            resolvedAuthorization = serviceToken.startsWith("Bearer ") ? serviceToken : "Bearer " + serviceToken;
            serviceTokenSent = true;
        }

        final String authorizationHeader = resolvedAuthorization;
        final boolean finalCallerAuthSent = callerAuthSent;
        final boolean finalServiceTokenSent = serviceTokenSent;

        TmsTimesheetBillingResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(TIMESHEETS_BILLING_PATH)
                            .queryParam("projectId", projectId)
                            .queryParam("billingPeriodStart", billingPeriodStart)
                            .queryParam("billingPeriodEnd", billingPeriodEnd)
                            .build())
                    .headers(headers -> {
                        if (authorizationHeader != null) {
                            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
                        }
                    })
                    .retrieve()
                    .body(TmsTimesheetBillingResponse.class);
        } catch (RestClientResponseException ex) {
            Metrics.counter("ar.tms.request.failures", "status", String.valueOf(ex.getStatusCode().value())).increment();
            throw translateTmsError(ex, finalCallerAuthSent, finalServiceTokenSent);
        } catch (RestClientException ex) {
            // Unreachable, timed out or unreadable response.
            Metrics.counter("ar.tms.request.failures", "status", "io").increment();
            throw ex;
        }

        if (response == null || response.getTimesheets() == null) {
            return Collections.emptyList();
        }

        return response.getTimesheets().stream()
                .map(this::toTimesheetDto)
                .collect(Collectors.toList());
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

    /** The incoming request's Authorization header; {@code null} outside a request (scheduler threads). */
    private static String currentAuthorizationHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        String header = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && !header.isBlank() ? header : null;
    }

    private RuntimeException translateTmsError(RestClientResponseException ex, boolean callerAuthorizationSent,
            boolean serviceTokenSent) {
        HttpStatusCode status = ex.getStatusCode();

        if (!callerAuthorizationSent && !serviceTokenSent && (status.value() == 401 || status.value() == 403)) {
            return new IllegalStateException("TMS refused the request (status " + status.value() + "): no "
                    + "Authorization was available - background TMS access requires service-to-service "
                    + "authentication (configure tms.api.service-token / TMS_SERVICE_TOKEN).");
        }

        if (status.value() == 400) {
            return new IllegalArgumentException("TMS rejected the request: " + ex.getResponseBodyAsString());
        }
        if (status.value() == 401) {
            if (serviceTokenSent) {
                return new IllegalStateException("TMS authentication failed: service-to-service token is invalid or expired.");
            }
            return new IllegalStateException("TMS authentication failed: the JWT is invalid or expired.");
        }
        if (status.value() == 403) {
            return new IllegalStateException("TMS authorization failed: insufficient permissions to read timesheets.");
        }
        if (status.is5xxServerError()) {
            return new TmsServiceUnavailableException("TMS service failed with status " + status.value() + ".");
        }
        return new IllegalStateException("Unexpected TMS response: " + status.value());
    }
}
