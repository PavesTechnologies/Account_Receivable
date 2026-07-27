package com.AccountReceivableManagement.Integration.billing_data_acquisition;

import com.AccountReceivableManagement.DTO.billing_data_acquisition.TimesheetDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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
 */
@Component
public class TimesheetIntegrationImpl implements TimesheetIntegration {

    private static final String TIMESHEETS_BILLING_PATH = "/api/timesheets/billing";

    private final RestClient restClient;
    private final HttpServletRequest httpServletRequest;

    public TimesheetIntegrationImpl(@Value("${tms.api.base-url}") String tmsApiBaseUrl,
                                     HttpServletRequest httpServletRequest) {
        this.restClient = RestClient.builder().baseUrl(tmsApiBaseUrl).build();
        this.httpServletRequest = httpServletRequest;
    }

    @Override
    public List<TimesheetDto> getApprovedTimesheets(Long projectId, LocalDate billingPeriodStart, LocalDate billingPeriodEnd) {
        String authorizationHeader = httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION);

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
            throw translateTmsError(ex);
        }

        if (response == null || response.getTimesheets() == null) {
            return Collections.emptyList();
        }

        return response.getTimesheets().stream()
                .map(this::toTimesheetDto)
                .collect(Collectors.toList());
    }

    private TimesheetDto toTimesheetDto(TmsTimesheetEntry entry) {
        // TMS guarantees every returned record is already approved and billable.
        return TimesheetDto.builder()
                .resourceId(entry.getResourceId())
                .resourceName(entry.getResourceName())
                .sourceReferenceId(entry.getTimesheetId())
                .hours(entry.getHours())
                .approved(true)
                .billable(true)
                .build();
    }

    private RuntimeException translateTmsError(RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();

        if (status.value() == 400) {
            return new IllegalArgumentException("TMS rejected the request: " + ex.getResponseBodyAsString());
        }
        if (status.value() == 401) {
            return new IllegalStateException("TMS authentication failed: the JWT is invalid or expired.");
        }
        if (status.value() == 403) {
            return new IllegalStateException("TMS authorization failed: insufficient permissions to read timesheets.");
        }
        if (status.is5xxServerError()) {
            return new IllegalStateException("TMS service failed with status " + status.value() + ".");
        }
        return new IllegalStateException("Unexpected TMS response: " + status.value());
    }
}
