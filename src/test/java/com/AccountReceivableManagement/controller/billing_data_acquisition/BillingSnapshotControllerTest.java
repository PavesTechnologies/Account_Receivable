package com.AccountReceivableManagement.controller.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotResponseDto;
import com.AccountReceivableManagement.dto.common.ApiResponse;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API contract of the Billing Snapshot read endpoints: the configuration-scoped
 * history list and the unchanged by-period lookup.
 */
@ExtendWith(MockitoExtension.class)
class BillingSnapshotControllerTest {

    @Mock
    private BillingSnapshotService billingSnapshotService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BillingSnapshotController(billingSnapshotService)).build();
    }

    @Test
    void listByBillingConfiguration_returnsSnapshotsWithTheirConfigurationId() throws Exception {
        UUID configurationId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        when(billingSnapshotService.getByBillingConfiguration(configurationId)).thenReturn(ApiResponse.success(
                "Billing Snapshots retrieved successfully.",
                List.of(BillingSnapshotResponseDto.builder()
                        .snapshotId(snapshotId)
                        .billingConfigurationId(configurationId)
                        .projectId(23L)
                        .status(BillingSnapshotStatus.INVOICED)
                        .build())));

        mockMvc.perform(get("/api/v1/billing-snapshots").param("billingConfigurationId", configurationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].snapshotId").value(snapshotId.toString()))
                .andExpect(jsonPath("$.data[0].billingConfigurationId").value(configurationId.toString()))
                .andExpect(jsonPath("$.data[0].status").value("INVOICED"))
                .andExpect(jsonPath("$.data[0].timesheets").isArray());
    }

    @Test
    void listByBillingConfiguration_noSnapshots_returnsEmptyArray() throws Exception {
        UUID configurationId = UUID.randomUUID();
        when(billingSnapshotService.getByBillingConfiguration(configurationId))
                .thenReturn(ApiResponse.success("No Billing Snapshots found for the selected billing configuration.",
                        List.of()));

        mockMvc.perform(get("/api/v1/billing-snapshots").param("billingConfigurationId", configurationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // There is no unfiltered listing: without a configuration nothing is returned.
    @Test
    void listByBillingConfiguration_withoutConfigurationId_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/billing-snapshots"))
                .andExpect(status().isBadRequest());

        verify(billingSnapshotService, never()).getByBillingConfiguration(any());
    }

    @Test
    void listByBillingConfiguration_malformedConfigurationId_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/billing-snapshots").param("billingConfigurationId", "PRJ-23"))
                .andExpect(status().isBadRequest());

        verify(billingSnapshotService, never()).getByBillingConfiguration(any());
    }

    // Backward compatibility: the existing by-period route still resolves to the by-period lookup.
    @Test
    void byPeriod_isUnchanged() throws Exception {
        UUID configurationId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 31);
        when(billingSnapshotService.getByProjectAndPeriod(23L, start, end, configurationId))
                .thenReturn(ApiResponse.success("Billing Snapshot retrieved successfully.",
                        BillingSnapshotResponseDto.builder().billingConfigurationId(configurationId).build()));

        mockMvc.perform(get("/api/v1/billing-snapshots/by-period")
                        .param("projectId", "23")
                        .param("billingPeriodStart", "2026-08-01")
                        .param("billingPeriodEnd", "2026-08-31")
                        .param("billingConfigurationId", configurationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.billingConfigurationId").value(configurationId.toString()));

        verify(billingSnapshotService, never()).getByBillingConfiguration(any());
    }
}
