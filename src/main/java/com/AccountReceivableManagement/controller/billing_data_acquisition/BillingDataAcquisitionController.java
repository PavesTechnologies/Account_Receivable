package com.AccountReceivableManagement.controller.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.AcquireDataResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionRequestDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingDataAcquisitionResponseDto;
import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingAcquisitionService;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingDataAcquisitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for the Billing Data Acquisition feature.
 *
 * Endpoints:
 *   GET  /api/billing-data-acquisition/active-configurations
 *   POST /api/billing-data-acquisition/acquire
 */
@RestController
@RequestMapping("/api/billing-data-acquisition")
@RequiredArgsConstructor
public class BillingDataAcquisitionController {

    private final BillingDataAcquisitionService billingDataAcquisitionService;
    private final BillingAcquisitionService billingAcquisitionService;

    /**
     * Returns all ACTIVE billing configurations formatted for the
     * Billing Data Acquisition overview table, sorted by project name,
     * including latest acquisition execution status.
     *
     * @return wrapped list of {@link BillingDataAcquisitionResponseDto}
     */
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
}
