package com.AccountReceivableManagement.controller.dashboard;

import com.AccountReceivableManagement.dto.dashboard.ArDashboardSummaryDto;
import com.AccountReceivableManagement.dto.dashboard.CollectionsDto;
import com.AccountReceivableManagement.dto.dashboard.DashboardInvoiceRowDto;
import com.AccountReceivableManagement.dto.dashboard.DashboardInvoiceView;
import com.AccountReceivableManagement.dto.dashboard.DashboardKpisDto;
import com.AccountReceivableManagement.dto.dashboard.DashboardPeriod;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.service_interface.dashboard.ArDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real controller behind the real exception handler, without a Spring context or a database. (Authentication is
 * enforced by SecurityConfig for every /api/** path and is covered there, not re-tested here.)
 */
@ExtendWith(MockitoExtension.class)
class ArDashboardControllerTest {

    @Mock
    private ArDashboardService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ArDashboardController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void summaryDefaultsToMonthToDateAsOfTodayAndUsesTheApiEnvelope() throws Exception {
        when(service.getSummary(DashboardPeriod.MTD, null)).thenReturn(ArDashboardSummaryDto.builder()
                .period(DashboardPeriod.MTD)
                .kpis(DashboardKpisDto.builder()
                        .collections(CollectionsDto.builder().tracked(false).reason("No payment data").build())
                        .build())
                .build());

        mvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.period").value("MTD"))
                .andExpect(jsonPath("$.data.kpis.collections.tracked").value(false))
                .andExpect(jsonPath("$.data.kpis.collections.collected").doesNotExist());
    }

    @Test
    void summaryParsesPeriodCaseInsensitivelyAndAnIsoAsOfDate() throws Exception {
        when(service.getSummary(DashboardPeriod.QTD, LocalDate.of(2026, 10, 22)))
                .thenReturn(ArDashboardSummaryDto.builder().period(DashboardPeriod.QTD).build());

        mvc.perform(get("/api/v1/dashboard/summary").param("period", "qtd").param("asOf", "2026-10-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("QTD"));
    }

    @Test
    void summaryRejectsAnUnknownPeriodWith400() throws Exception {
        mvc.perform(get("/api/v1/dashboard/summary").param("period", "WEEKLY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("MTD, QTD or YTD")));
        verifyNoInteractions(service);
    }

    @Test
    void summaryRejectsAMalformedAsOfWith400() throws Exception {
        mvc.perform(get("/api/v1/dashboard/summary").param("asOf", "22/10/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("yyyy-MM-dd")));
        verifyNoInteractions(service);
    }

    @Test
    void invoicesPassViewSearchLimitAndAsOfThrough() throws Exception {
        when(service.getInvoices(DashboardInvoiceView.AWAITING_SEND, "acme", 10, LocalDate.of(2026, 10, 22)))
                .thenReturn(List.of(DashboardInvoiceRowDto.builder()
                        .invoiceNumber("INV-1").grandTotal(new BigDecimal("118.00"))
                        .displayStatus("AWAITING_SEND").build()));

        mvc.perform(get("/api/v1/dashboard/invoices")
                        .param("view", "awaiting-send").param("q", "acme").param("limit", "10").param("asOf", "2026-10-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].invoiceNumber").value("INV-1"))
                .andExpect(jsonPath("$.data[0].displayStatus").value("AWAITING_SEND"));
    }

    @Test
    void invoicesDefaultToAllWithNoLimit() throws Exception {
        when(service.getInvoices(DashboardInvoiceView.ALL, null, null, null)).thenReturn(List.of());

        mvc.perform(get("/api/v1/dashboard/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        verify(service).getInvoices(DashboardInvoiceView.ALL, null, null, null);
    }

    @Test
    void invoicesRejectAnUnknownViewOrANonNumericLimitWith400() throws Exception {
        mvc.perform(get("/api/v1/dashboard/invoices").param("view", "PAID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/api/v1/dashboard/invoices").param("limit", "ten"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("limit")));
        verifyNoInteractions(service);
    }

    @Test
    void serviceValidationErrorsAlsoBecome400() throws Exception {
        when(service.getInvoices(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("limit must be at least 1."));

        mvc.perform(get("/api/v1/dashboard/invoices").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("limit must be at least 1."));
    }
}
