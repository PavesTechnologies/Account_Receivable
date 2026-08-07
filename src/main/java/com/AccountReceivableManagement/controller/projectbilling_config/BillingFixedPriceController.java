package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingFixedPriceRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingFixedPriceResponseDto;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingFixedPriceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/billing-fixed-price")
@RequiredArgsConstructor
public class BillingFixedPriceController {

    private final BillingFixedPriceService billingFixedPriceService;

    @PostMapping("/{billingConfigurationId}/fixed-price")
    public ResponseEntity<ApiResponse<BillingFixedPriceResponseDto>> create(
            @PathVariable UUID billingConfigurationId,
            @Valid @RequestBody BillingFixedPriceRequestDto request) {

        BillingFixedPriceResponseDto response =
                billingFixedPriceService.create(
                        billingConfigurationId,
                        request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<BillingFixedPriceResponseDto>builder()
                        .success(true)
                        .message("Fixed Price configuration created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/fixed-price/{fixedPriceConfigurationId}")
    public ResponseEntity<ApiResponse<BillingFixedPriceResponseDto>> update(
            @PathVariable UUID fixedPriceConfigurationId,
            @Valid @RequestBody BillingFixedPriceRequestDto request) {

        BillingFixedPriceResponseDto response =
                billingFixedPriceService.update(
                        fixedPriceConfigurationId,
                        request);

        return ResponseEntity.ok(
                ApiResponse.<BillingFixedPriceResponseDto>builder()
                        .success(true)
                        .message("Fixed Price configuration updated successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping("/fixed-price/{fixedPriceConfigurationId}")
    public ResponseEntity<ApiResponse<BillingFixedPriceResponseDto>> get(
            @PathVariable UUID fixedPriceConfigurationId) {

        BillingFixedPriceResponseDto response =
                billingFixedPriceService.get(fixedPriceConfigurationId);

        return ResponseEntity.ok(
                ApiResponse.<BillingFixedPriceResponseDto>builder()
                        .success(true)
                        .message("Fixed Price configuration fetched successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping("/{billingConfigurationId}/fixed-price")
    public ResponseEntity<ApiResponse<List<BillingFixedPriceResponseDto>>> getAll(
            @PathVariable UUID billingConfigurationId) {

        List<BillingFixedPriceResponseDto> response =
                billingFixedPriceService.getAll(billingConfigurationId);

        return ResponseEntity.ok(
                ApiResponse.<List<BillingFixedPriceResponseDto>>builder()
                        .success(true)
                        .message("Fixed Price configurations fetched successfully.")
                        .data(response)
                        .build());
    }

    @DeleteMapping("/fixed-price/{fixedPriceConfigurationId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID fixedPriceConfigurationId) {

        billingFixedPriceService.delete(fixedPriceConfigurationId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Fixed Price configuration deleted successfully.")
                        .build());
    }
}
