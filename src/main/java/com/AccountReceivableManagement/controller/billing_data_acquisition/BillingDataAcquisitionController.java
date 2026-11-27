package com.AccountReceivableManagement.controller.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.AcquireDataResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionRequestDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingDataAcquisitionResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingPeriodDiscoveryResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.IncrementalBatchRequestDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.NextBillingPeriodResponseDto;
import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingAcquisitionService;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingDataAcquisitionService;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingPeriodAcquisitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import com.AccountReceivableManagement.security.ExecutiveOnly;

/**
 * REST controller for the Billing Data Acquisition feature.
 *
 * Endpoints:
 *   GET  /api/billing-data-acquisition/active-configurations
 *   POST /api/billing-data-acquisition/acquire
 *   GET  /api/billing-data-acquisition/configurations/{billingConfigurationId}/periods
 *   GET  /api/billing-data-acquisition/configurations/{billingConfigurationId}/next-period
 *   POST /api/billing-data-acquisition/configurations/{billingConfigurationId}/batches
 */
@RestController
@RequestMapping("/api/billing-data-acquisition")
@RequiredArgsConstructor
public class BillingDataAcquisitionController {

    private final BillingDataAcquisitionService billingDataAcquisitionService;
    private final BillingAcquisitionService billingAcquisitionService;
    private final BillingPeriodAcquisitionService billingPeriodAcquisitionService;

    /**
     * Returns all ACTIVE billing configurations formatted for the
     * Billing Data Acquisition overview table, sorted by project name,
     * including latest acquisition execution status.
     *
     * @return wrapped list of {@link BillingDataAcquisitionResponseDto}
     */
    @ExecutiveOnly
    @GetMapping("/active-configurations")
    public ResponseEntity<ApiResponse<List<BillingDataAcquisitionResponseDto>>> getActiveConfigurations() {

        List<BillingDataAcquisitionResponseDto> data =
                billingDataAcquisitionService.getActiveConfigurations();

        return ResponseEntity.ok(
                ApiResponse.<List<BillingDataAcquisitionResponseDto>>builder()
                        .success(true)
                        .message("Active billing configurations retrieved successfully.")
                        .data(data)
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    /**
     * Creates or updates a manual billing acquisition execution record.
     * Sets status to READY and triggerMode to MANUAL.
     *
     * @param request {@link BillingAcquisitionRequestDto}
     * @return wrapped {@link AcquireDataResponseDto}
     */
    @ExecutiveOnly
    @PostMapping("/acquire")
    public ResponseEntity<ApiResponse<AcquireDataResponseDto>> acquire(
            @Valid @RequestBody BillingAcquisitionRequestDto request) {

        AcquireDataResponseDto response = billingAcquisitionService.createManualAcquisition(request);

        return ResponseEntity.ok(
                ApiResponse.<AcquireDataResponseDto>builder()
                        .success(true)
                        .message("Billing acquisition completed successfully.")
                        .data(response)
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    /**
     * Incremental Time &amp; Material billing - every billing period of the configuration up to the one
     * containing today, previously billed periods included, with newly eligible (approved, billable,
     * unclaimed) timesheet and expense counts, billable hours and amounts, rate validation, readiness,
     * blocking reasons and the period's existing batches and invoices. Read-only.
     */
    @ExecutiveOnly
    @GetMapping("/configurations/{billingConfigurationId}/periods")
    public ResponseEntity<ApiResponse<BillingPeriodDiscoveryResponseDto>> getBillingPeriods(
            @PathVariable UUID billingConfigurationId,
            @RequestParam(name = "onlyWithNewItems", defaultValue = "false") boolean onlyWithNewItems) {

        BillingPeriodDiscoveryResponseDto data =
                billingPeriodAcquisitionService.discoverPeriods(billingConfigurationId, onlyWithNewItems);

        return ResponseEntity.ok(
                ApiResponse.<BillingPeriodDiscoveryResponseDto>builder()
                        .success(true)
                        .message("Billing periods retrieved successfully.")
                        .data(data)
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    /**
     * The single billing period to acquire next for this configuration (derived from period discovery).
     * Read-only: creates no snapshot and claims no timesheet. A TMS/XMS outage is reported as
     * {@code acquisitionAllowed=false} with a reason, not as an HTTP error.
     */
    @ExecutiveOnly
    @GetMapping("/configurations/{billingConfigurationId}/next-period")
    public ResponseEntity<ApiResponse<NextBillingPeriodResponseDto>> getNextBillingPeriod(
            @PathVariable UUID billingConfigurationId) {

        NextBillingPeriodResponseDto data = billingPeriodAcquisitionService.nextPeriod(billingConfigurationId);

        return ResponseEntity.ok(
                ApiResponse.<NextBillingPeriodResponseDto>builder()
                        .success(true)
                        .message(data.getReason())
                        .data(data)
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    /**
     * Incremental Time &amp; Material billing - acquires the newly eligible records of one billing period as
     * a new, independently invoiceable batch (snapshot + acquisition record). Existing snapshots and
     * invoices of the period are never changed. {@code success=false} (HTTP 200, as POST
     * /api/v1/billing-snapshots) when nothing new exists or validation fails; nothing is created then.
     */
    @ExecutiveOnly
    @PostMapping("/configurations/{billingConfigurationId}/batches")
    public ResponseEntity<com.AccountReceivableManagement.dto.common.ApiResponse<BillingSnapshotResponseDto>> acquireBatch(
            @PathVariable UUID billingConfigurationId,
            @Valid @RequestBody IncrementalBatchRequestDto request) {

        return ResponseEntity.ok(billingPeriodAcquisitionService.acquireBatch(billingConfigurationId, request));
    }
}
