package com.AccountReceivableManagement.controller.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionSummaryDto;
import com.AccountReceivableManagement.dto.centralizeddto.PagedResponse;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingAcquisitionService;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingDataAcquisitionService;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingPeriodAcquisitionService;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/billing-data-acquisition - the paginated listing: parameter defaults and
 * clamping, billing type parsing, and the JSON shape the frontend table binds to.
 */
@ExtendWith(MockitoExtension.class)
class BillingDataAcquisitionListingControllerTest {

    private static final String BASE = "/api/billing-data-acquisition";
    private static final UUID CONFIG_ID = UUID.fromString("92e94061-a911-4092-a07c-d164ded1f1ce");

    @Mock private BillingDataAcquisitionService billingDataAcquisitionService;
    @Mock private BillingAcquisitionService billingAcquisitionService;
    @Mock private BillingPeriodAcquisitionService billingPeriodAcquisitionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BillingDataAcquisitionController(
                        billingDataAcquisitionService, billingAcquisitionService, billingPeriodAcquisitionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    private static PagedResponse<BillingAcquisitionSummaryDto> onePage() {
        return PagedResponse.<BillingAcquisitionSummaryDto>builder()
                .content(List.of(BillingAcquisitionSummaryDto.builder()
                        .billingConfigurationId(CONFIG_ID)
                        .clientName("Acme Corp")
                        .projectName("ERP Rollout")
                        .projectCode("ACME-ERP-01")
                        .billingType("Timesheet Based")
                        .status("NOT_ACQUIRED")
                        .build()))
                .page(0).size(10).totalElements(1).totalPages(1).first(true).last(true)
                .build();
    }

    @Test
    void listing_defaultsToPageZeroAndSizeTen_withNoBillingTypeFilter() throws Exception {
        when(billingDataAcquisitionService.getAcquisitionOverview(isNull(), eq(0), eq(10)))
                .thenReturn(onePage());

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Billing data acquisition records retrieved successfully."))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(billingDataAcquisitionService).getAcquisitionOverview(null, 0, 10);
    }

    @Test
    void listing_returnsOnlyTheSixAgreedFields_perRow() throws Exception {
        when(billingDataAcquisitionService.getAcquisitionOverview(isNull(), anyInt(), anyInt()))
                .thenReturn(onePage());

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].billingConfigurationId").value(CONFIG_ID.toString()))
                .andExpect(jsonPath("$.data.content[0].clientName").value("Acme Corp"))
                .andExpect(jsonPath("$.data.content[0].projectName").value("ERP Rollout"))
                .andExpect(jsonPath("$.data.content[0].projectCode").value("ACME-ERP-01"))
                .andExpect(jsonPath("$.data.content[0].billingType").value("Timesheet Based"))
                .andExpect(jsonPath("$.data.content[0].status").value("NOT_ACQUIRED"))
                // "reference" was explicitly dropped from the payload.
                .andExpect(jsonPath("$.data.content[0].reference").doesNotExist())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    void listing_keepsProjectCodeKeyPresentWhenTheColumnIsNull() throws Exception {
        when(billingDataAcquisitionService.getAcquisitionOverview(isNull(), anyInt(), anyInt()))
                .thenReturn(PagedResponse.<BillingAcquisitionSummaryDto>builder()
                        .content(List.of(BillingAcquisitionSummaryDto.builder()
                                .billingConfigurationId(CONFIG_ID).projectCode(null).build()))
                        .page(0).size(10).totalElements(1).totalPages(1).first(true).last(true).build());

        // The key must be present and null, not omitted: the table binds to it.
        String body = mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"projectCode\":null");
    }

    @Test
    void listing_passesTheRequestedBillingTypeThrough() throws Exception {
        when(billingDataAcquisitionService.getAcquisitionOverview(
                eq(BillingType.TIME_AND_MATERIAL), anyInt(), anyInt())).thenReturn(onePage());

        mockMvc.perform(get(BASE).param("billingType", "TIME_AND_MATERIAL"))
                .andExpect(status().isOk());

        verify(billingDataAcquisitionService)
                .getAcquisitionOverview(BillingType.TIME_AND_MATERIAL, 0, 10);
    }

    @Test
    void listing_acceptsTheBillingTypeCaseInsensitively() throws Exception {
        when(billingDataAcquisitionService.getAcquisitionOverview(
                eq(BillingType.TIME_AND_MATERIAL), anyInt(), anyInt())).thenReturn(onePage());

        mockMvc.perform(get(BASE).param("billingType", " time_and_material "))
                .andExpect(status().isOk());

        verify(billingDataAcquisitionService)
                .getAcquisitionOverview(BillingType.TIME_AND_MATERIAL, 0, 10);
    }

    @Test
    void listing_blankBillingTypeIsTreatedAsNoFilter() throws Exception {
        when(billingDataAcquisitionService.getAcquisitionOverview(isNull(), anyInt(), anyInt()))
                .thenReturn(onePage());

        mockMvc.perform(get(BASE).param("billingType", "  "))
                .andExpect(status().isOk());

        verify(billingDataAcquisitionService).getAcquisitionOverview(null, 0, 10);
    }

    @Test
    void listing_unknownBillingTypeIsRejectedWithoutCallingTheService() throws Exception {
        mockMvc.perform(get(BASE).param("billingType", "SUBSCRIPTION_PLAN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Unknown billingType 'SUBSCRIPTION_PLAN'")));

        verify(billingDataAcquisitionService, never()).getAcquisitionOverview(any(), anyInt(), anyInt());
    }

    @Test
    void listing_clampsSizeToTheMaximum_andFloorsNegativePageAndSize() throws Exception {
        when(billingDataAcquisitionService.getAcquisitionOverview(isNull(), anyInt(), anyInt()))
                .thenReturn(onePage());

        mockMvc.perform(get(BASE).param("size", "100000").param("page", "-3"))
                .andExpect(status().isOk());

        ArgumentCaptor<Integer> pageCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(billingDataAcquisitionService)
                .getAcquisitionOverview(isNull(), pageCaptor.capture(), sizeCaptor.capture());

        assertThat(pageCaptor.getValue()).isZero();
        assertThat(sizeCaptor.getValue()).isEqualTo(BillingDataAcquisitionController.MAX_PAGE_SIZE);
    }

    @Test
    void listing_doesNotShadowTheActiveConfigurationsEndpoint() throws Exception {
        when(billingDataAcquisitionService.getActiveConfigurations()).thenReturn(List.of());

        mockMvc.perform(get(BASE + "/active-configurations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Active billing configurations retrieved successfully."));

        verify(billingDataAcquisitionService, never()).getAcquisitionOverview(any(), anyInt(), anyInt());
    }
}
