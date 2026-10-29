package com.AccountReceivableManagement.controller.dashboard;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.dashboard.ArDashboardSummaryDto;
import com.AccountReceivableManagement.dto.dashboard.DashboardInvoiceRowDto;
import com.AccountReceivableManagement.dto.dashboard.DashboardInvoiceView;
import com.AccountReceivableManagement.dto.dashboard.DashboardPeriod;
import com.AccountReceivableManagement.service_interface.dashboard.ArDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Read-only AR dashboard data.
 *
 * <p>Query parameters are taken as strings and validated here so a bad value
 * returns a 400 with a clear message (rather than a type-conversion 500).
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class ArDashboardController {

    private final ArDashboardService dashboardService;

    /**
     * KPIs, billing-cycle pipeline, trend, aging, billing-model mix, worklist and recent activity.
     *
     * @param period MTD (default), QTD or YTD
     * @param asOf   reference date, yyyy-MM-dd; defaults to today
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ArDashboardSummaryDto>> getSummary(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String asOf) {
        ArDashboardSummaryDto summary = dashboardService.getSummary(DashboardPeriod.parse(period), parseDate(asOf));
        return ResponseEntity.ok(ApiResponse.<ArDashboardSummaryDto>builder()
                .success(true)
                .message("AR dashboard summary retrieved successfully.")
                .data(summary)
                .timestamp(LocalDateTime.now())
                .build());
    }

    /**
     * Dashboard ledger, soonest due first.
     *
     * @param view  ALL (default), OVERDUE, PENDING_APPROVAL, AWAITING_SEND or SENT
     * @param q     text matched against invoice number, client and project
     * @param limit rows to return (default 50, capped at 200)
     * @param asOf  reference date for overdue/aging, yyyy-MM-dd; defaults to today
     */
    @GetMapping("/invoices")
    public ResponseEntity<ApiResponse<List<DashboardInvoiceRowDto>>> getInvoices(
            @RequestParam(required = false) String view,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String asOf) {
        List<DashboardInvoiceRowDto> rows = dashboardService.getInvoices(
                DashboardInvoiceView.parse(view), q, parseLimit(limit), parseDate(asOf));
        return ResponseEntity.ok(ApiResponse.<List<DashboardInvoiceRowDto>>builder()
                .success(true)
                .message("AR dashboard invoices retrieved successfully.")
                .data(rows)
                .timestamp(LocalDateTime.now())
                .build());
    }

    static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid asOf '" + value + "'. Use the yyyy-MM-dd format.");
        }
    }

    static Integer parseLimit(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid limit '" + value + "'. Use a whole number from 1 to 200.");
        }
    }
}
