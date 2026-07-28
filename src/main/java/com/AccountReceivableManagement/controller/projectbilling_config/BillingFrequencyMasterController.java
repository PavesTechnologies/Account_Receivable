package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingFrequencyRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingFrequencyResponseDto;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingFrequencyMasterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/billing-frequency")
@RequiredArgsConstructor
public class BillingFrequencyMasterController {

    private final BillingFrequencyMasterService billingFrequencyService;

    @PostMapping
    public ResponseEntity<ApiResponse<BillingFrequencyResponseDto>> createBillingFrequency(
            @Valid @RequestBody BillingFrequencyRequestDto request) {

        BillingFrequencyResponseDto response = billingFrequencyService.createBillingFrequency(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<BillingFrequencyResponseDto>builder()
                        .success(true)
                        .message("Billing Frequency created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/{billingFrequencyId}")
    public ResponseEntity<ApiResponse<BillingFrequencyResponseDto>> updateBillingFrequency(
            @PathVariable UUID billingFrequencyId,
            @Valid @RequestBody BillingFrequencyRequestDto request) {

        BillingFrequencyResponseDto response =
                billingFrequencyService.updateBillingFrequency(billingFrequencyId, request);

        return ResponseEntity.ok(ApiResponse.<BillingFrequencyResponseDto>builder()
                .success(true)
                .message("Billing Frequency updated successfully.")
                .data(response)
                .build());
    }

    @GetMapping("/{billingFrequencyId}")
    public ResponseEntity<ApiResponse<BillingFrequencyResponseDto>> getBillingFrequencyById(
            @PathVariable UUID billingFrequencyId) {

        BillingFrequencyResponseDto response =
                billingFrequencyService.getBillingFrequencyById(billingFrequencyId);

        return ResponseEntity.ok(ApiResponse.<BillingFrequencyResponseDto>builder()
                .success(true)
                .message("Billing Frequency retrieved successfully.")
                .data(response)
                .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BillingFrequencyResponseDto>>> getAllBillingFrequencies() {

        List<BillingFrequencyResponseDto> response =
                billingFrequencyService.getAllBillingFrequencies();

        return ResponseEntity.ok(ApiResponse.<List<BillingFrequencyResponseDto>>builder()
                .success(true)
                .message("Billing Frequencies retrieved successfully.")
                .data(response)
                .build());
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<BillingFrequencyResponseDto>>> getActiveBillingFrequencies() {

        List<BillingFrequencyResponseDto> response =
                billingFrequencyService.getActiveBillingFrequencies();

        return ResponseEntity.ok(ApiResponse.<List<BillingFrequencyResponseDto>>builder()
                .success(true)
                .message("Active Billing Frequencies retrieved successfully.")
                .data(response)
                .build());
    }

    @DeleteMapping("/{billingFrequencyId}")
    public ResponseEntity<ApiResponse<Void>> deleteBillingFrequency(
            @PathVariable UUID billingFrequencyId) {

        billingFrequencyService.deleteBillingFrequency(billingFrequencyId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Billing Frequency deleted successfully.")
                .data(null)
                .build());
    }
}
