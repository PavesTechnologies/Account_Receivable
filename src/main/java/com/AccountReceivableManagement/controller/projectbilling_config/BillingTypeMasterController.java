package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingTypeRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingTypeResponseDto;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingTypeMasterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/billing-types")
@RequiredArgsConstructor
public class BillingTypeMasterController {

    private final BillingTypeMasterService billingTypeService;

    @PostMapping
    public ResponseEntity<ApiResponse<BillingTypeResponseDto>> createBillingType(
            @Valid @RequestBody BillingTypeRequestDto request) {

        BillingTypeResponseDto response =
                billingTypeService.createBillingType(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<BillingTypeResponseDto>builder()
                        .success(true)
                        .message("Billing Type created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/{billingTypeId}")
    public ResponseEntity<ApiResponse<BillingTypeResponseDto>> updateBillingType(
            @PathVariable UUID billingTypeId,
            @Valid @RequestBody BillingTypeRequestDto request) {

        BillingTypeResponseDto response =
                billingTypeService.updateBillingType(billingTypeId, request);

        return ResponseEntity.ok(
                ApiResponse.<BillingTypeResponseDto>builder()
                        .success(true)
                        .message("Billing Type updated successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping("/{billingTypeId}")
    public ResponseEntity<ApiResponse<BillingTypeResponseDto>> getBillingTypeById(
            @PathVariable UUID billingTypeId) {

        BillingTypeResponseDto response =
                billingTypeService.getBillingTypeById(billingTypeId);

        return ResponseEntity.ok(
                ApiResponse.<BillingTypeResponseDto>builder()
                        .success(true)
                        .message("Billing Type retrieved successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BillingTypeResponseDto>>> getAllBillingTypes() {

        List<BillingTypeResponseDto> response =
                billingTypeService.getAllBillingTypes();

        return ResponseEntity.ok(
                ApiResponse.<List<BillingTypeResponseDto>>builder()
                        .success(true)
                        .message("Billing Types retrieved successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<BillingTypeResponseDto>>> getActiveBillingTypes() {

        List<BillingTypeResponseDto> response =
                billingTypeService.getActiveBillingTypes();

        return ResponseEntity.ok(
                ApiResponse.<List<BillingTypeResponseDto>>builder()
                        .success(true)
                        .message("Active Billing Types retrieved successfully.")
                        .data(response)
                        .build());
    }

    @DeleteMapping("/{billingTypeId}")
    public ResponseEntity<ApiResponse<Void>> deleteBillingType(
            @PathVariable UUID billingTypeId) {

        billingTypeService.deleteBillingType(billingTypeId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Billing Type deleted successfully.")
                        .data(null)
                        .build());
    }
}
