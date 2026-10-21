package com.AccountReceivableManagement.controller.operations;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.operations.BillingOperationsSummaryDto;
import com.AccountReceivableManagement.dto.operations.OperationsAlertDto;
import com.AccountReceivableManagement.dto.operations.ReconciliationReportDto;
import com.AccountReceivableManagement.service_interface.operations.OperationsReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only operations reports for Finance and AR support. Access is limited
 * to the authorities in {@code ar.operations.report-authorities}
 * (SecurityConfig); nothing here writes.
 */
@RestController
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
public class OperationsReportController {

    private final OperationsReportService reportService;

    /** Automatic billing execution status next to invoice and receivable business status. */
    @GetMapping("/billing-summary")
    public ResponseEntity<ApiResponse<BillingOperationsSummaryDto>> getBillingSummary(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ApiResponse.<BillingOperationsSummaryDto>builder()
                .success(true)
                .message("Billing operations summary retrieved successfully.")
                .data(reportService.getSummary(days))
                .build());
    }

    /** Financial reconciliation findings - for investigation; nothing is corrected. */
    @GetMapping("/reconciliation")
    public ResponseEntity<ApiResponse<ReconciliationReportDto>> getReconciliation() {
        return ResponseEntity.ok(ApiResponse.<ReconciliationReportDto>builder()
                .success(true)
                .message("Reconciliation report generated successfully.")
                .data(reportService.reconcile())
                .build());
    }

    /** Configured alert conditions with their current values. */
    @GetMapping("/alerts")
    public ResponseEntity<ApiResponse<List<OperationsAlertDto>>> getAlerts() {
        return ResponseEntity.ok(ApiResponse.<List<OperationsAlertDto>>builder()
                .success(true)
                .message("Alert conditions evaluated successfully.")
                .data(reportService.evaluateAlerts())
                .build());
    }
}
