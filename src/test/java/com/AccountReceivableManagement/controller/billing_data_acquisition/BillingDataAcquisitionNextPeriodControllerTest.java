package com.AccountReceivableManagement.controller.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingPeriodDiscoveryResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.NextBillingPeriodResponseDto;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingAcquisitionService;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingDataAcquisitionService;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingPeriodAcquisitionService;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Routing of GET .../configurations/{id}/next-period: it used to have no handler, so Spring's static-resource
 * fallback answered "Resource not found." without ever looking up the configuration.
 */
@ExtendWith(MockitoExtension.class)
class BillingDataAcquisitionNextPeriodControllerTest {

    /** The configuration id from the reported request; it is a billing configuration id, not a project id. */
    private static final UUID CONFIG_ID = UUID.fromString("a0722e89-ec83-4450-939f-aa20c136cbd4");
    private static final String BASE = "/api/billing-data-acquisition/configurations/";

    @Mock private BillingDataAcquisitionService billingDataAcquisitionService;
    @Mock private BillingAcquisitionService billingAcquisitionService;
    @Mock private BillingPeriodAcquisitionService billingPeriodAcquisitionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BillingDataAcquisitionController(
                        billingDataAcquisitionService, billingAcquisitionService, billingPeriodAcquisitionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                // ISO dates, as Spring Boot's auto-configured ObjectMapper writes them
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    @Test
    void nextPeriod_isRouted_andPassesThePathUuidAsTheBillingConfigurationId() throws Exception {
        when(billingPeriodAcquisitionService.nextPeriod(CONFIG_ID)).thenReturn(NextBillingPeriodResponseDto.builder()
                .billingConfigurationId(CONFIG_ID).projectId(23L).billingFrequency("Weekly")
                .billingPeriodStart(LocalDate.of(2026, 11, 4)).billingPeriodEnd(LocalDate.of(2026, 11, 10))
                .periodEnded(true).acquisitionAllowed(true).eligibleRecordCount(2)
                .eligibleHours(new BigDecimal("12")).reason("ready").build());

        mockMvc.perform(get(BASE + CONFIG_ID + "/next-period"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.billingConfigurationId").value(CONFIG_ID.toString()))
                .andExpect(jsonPath("$.data.projectId").value(23))
                .andExpect(jsonPath("$.data.billingPeriodStart").value("2026-11-04"))
                .andExpect(jsonPath("$.data.billingPeriodEnd").value("2026-11-10"))
                .andExpect(jsonPath("$.data.acquisitionAllowed").value(true))
                .andExpect(jsonPath("$.data.eligibleRecordCount").value(2))
                .andExpect(jsonPath("$.data.alreadyClaimedTimesheetIds").isArray());
        verify(billingPeriodAcquisitionService).nextPeriod(CONFIG_ID);
    }

    @Test
    void nextPeriod_missingConfiguration_isStillRejected() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(billingPeriodAcquisitionService.nextPeriod(unknown))
                .thenThrow(new GlobalExceptionHandler.ValidationException("Approved Billing Configuration not found."));

        mockMvc.perform(get(BASE + unknown + "/next-period"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Approved Billing Configuration not found."));
    }

    @Test
    void nextPeriod_malformedId_isRejectedWithoutCallingTheService() throws Exception {
        // Status is decided by GlobalExceptionHandler's existing generic handler (same as for .../periods).
        mockMvc.perform(get(BASE + "not-a-uuid/next-period"))
                .andExpect(jsonPath("$.success").value(false));
        verify(billingPeriodAcquisitionService, never()).nextPeriod(any());
    }

    @Test
    void periods_isStillRoutedUnchanged() throws Exception {
        when(billingPeriodAcquisitionService.discoverPeriods(CONFIG_ID, false)).thenReturn(
                BillingPeriodDiscoveryResponseDto.builder().billingConfigurationId(CONFIG_ID).projectId(23L).build());

        mockMvc.perform(get(BASE + CONFIG_ID + "/periods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Billing periods retrieved successfully."))
                .andExpect(jsonPath("$.data.billingConfigurationId").value(CONFIG_ID.toString()));
        verify(billingPeriodAcquisitionService, never()).nextPeriod(any());
    }
}
