package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingSubscriptionRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingSubscriptionResponseDto;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.UUID;

@RestController
@RequestMapping("/billing-subscription")
@RequiredArgsConstructor
public class BillingSubscriptionController {

    private final BillingSubscriptionService billingSubscriptionService;

    @PostMapping("/{billingConfigurationId}")
    public ResponseEntity<ApiResponse<BillingSubscriptionResponseDto>> create(
            @PathVariable UUID billingConfigurationId,
            @Valid @RequestBody BillingSubscriptionRequestDto request) {

        BillingSubscriptionResponseDto response =
                billingSubscriptionService.create(
                        billingConfigurationId,
                        request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<BillingSubscriptionResponseDto>builder()
                        .success(true)
                        .message("Subscription configuration created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/{subscriptionConfigurationId}")
    public ResponseEntity<ApiResponse<BillingSubscriptionResponseDto>> update(
            @PathVariable UUID subscriptionConfigurationId,
            @Valid @RequestBody BillingSubscriptionRequestDto request) {

        BillingSubscriptionResponseDto response =
                billingSubscriptionService.update(
                        subscriptionConfigurationId,
                        request);

        return ResponseEntity.ok(
                ApiResponse.<BillingSubscriptionResponseDto>builder()
                        .success(true)
                        .message("Subscription configuration updated successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping("/{subscriptionConfigurationId}")
    public ResponseEntity<ApiResponse<BillingSubscriptionResponseDto>> get(
            @PathVariable UUID subscriptionConfigurationId) {

        BillingSubscriptionResponseDto response =
                billingSubscriptionService.get(
                        subscriptionConfigurationId);

        return ResponseEntity.ok(
                ApiResponse.<BillingSubscriptionResponseDto>builder()
                        .success(true)
                        .message("Subscription configuration fetched successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping("/billing-configuration/{billingConfigurationId}")
    public ResponseEntity<ApiResponse<BillingSubscriptionResponseDto>> getByBillingConfiguration(
            @PathVariable UUID billingConfigurationId) {

        BillingSubscriptionResponseDto response =
                billingSubscriptionService.getByBillingConfiguration(
                        billingConfigurationId);

        return ResponseEntity.ok(
                ApiResponse.<BillingSubscriptionResponseDto>builder()
                        .success(true)
                        .message("Subscription configuration fetched successfully.")
                        .data(response)
                        .build());
    }

    @DeleteMapping("/{subscriptionConfigurationId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID subscriptionConfigurationId) {

        billingSubscriptionService.delete(subscriptionConfigurationId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Subscription configuration deleted successfully.")
                        .build());
    }
}
