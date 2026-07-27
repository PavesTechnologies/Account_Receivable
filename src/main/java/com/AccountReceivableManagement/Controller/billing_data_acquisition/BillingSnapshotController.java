package com.AccountReceivableManagement.Controller.billing_data_acquisition;

import com.AccountReceivableManagement.DTO.billing_data_acquisition.BillingSnapshotCreateRequestDto;
import com.AccountReceivableManagement.DTO.billing_data_acquisition.BillingSnapshotResponseDto;
import com.AccountReceivableManagement.DTO.common.ApiResponse;
import com.AccountReceivableManagement.Service_Interface.billing_data_acquisition.BillingSnapshotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Billing Snapshot operations.
 *
 * Story 2.1:
 * Creates a Billing Snapshot by acquiring billing data
 * for a Time & Material project.
 *
 * Controller Responsibility:
 * - Accept request
 * - Delegate to Service
 * - Return response
 *
 * No business logic should exist here.
 */
@RestController
@RequestMapping("/api/v1/billing-snapshots")
@RequiredArgsConstructor
public class BillingSnapshotController {

    private final BillingSnapshotService billingSnapshotService;

    /**
     * Creates a Billing Snapshot.
     *
     * Flow:
     * Request
     *      ↓
     * Service
     *      ↓
     * Integration
     *      ↓
     * Strategy
     *      ↓
     * Validator
     *      ↓
     * Builder
     *      ↓
     * Repository
     *
     * @param request Billing Snapshot request
     * @return Billing Snapshot response
     */
    @PostMapping
    public ResponseEntity<ApiResponse<BillingSnapshotResponseDto>> createBillingSnapshot(
            @Valid @RequestBody BillingSnapshotCreateRequestDto request) {

        ApiResponse<BillingSnapshotResponseDto> response =
                billingSnapshotService.createBillingSnapshot(request);

        return ResponseEntity.ok(response);
    }
}
