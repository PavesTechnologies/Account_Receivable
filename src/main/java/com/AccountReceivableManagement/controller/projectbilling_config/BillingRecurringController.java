package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingPeriodDto;
import com.AccountReceivableManagement.dto.projectbilling_config.RecurringBillingRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.RecurringBillingResponseDto;
import com.AccountReceivableManagement.service_interface.projectbilling_config.RecurringBillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/billing-recurring")
@RequiredArgsConstructor
public class BillingRecurringController {

    private final RecurringBillingService recurringBillingService;

    @PostMapping("/{billingConfigurationId}")
    public ResponseEntity<ApiResponse<RecurringBillingResponseDto>> create(
            @PathVariable UUID billingConfigurationId,
            @Valid @RequestBody RecurringBillingRequestDto request) {

        RecurringBillingResponseDto response =
                recurringBillingService.create(billingConfigurationId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<RecurringBillingResponseDto>builder()
                        .success(true)
                        .message("Recurring billing configuration created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/{recurringConfigurationId}")
    public ResponseEntity<ApiResponse<RecurringBillingResponseDto>> update(
            @PathVariable UUID recurringConfigurationId,
            @Valid @RequestBody RecurringBillingRequestDto request) {

        RecurringBillingResponseDto response =
                recurringBillingService.update(recurringConfigurationId, request);

        return ResponseEntity.ok(ApiResponse.<RecurringBillingResponseDto>builder()
                .success(true)
                .message("Recurring billing configuration updated successfully.")
                .data(response)
                .build());
    }

    @GetMapping("/{recurringConfigurationId}")
    public ResponseEntity<ApiResponse<RecurringBillingResponseDto>> get(
            @PathVariable UUID recurringConfigurationId) {

        RecurringBillingResponseDto response =
                recurringBillingService.get(recurringConfigurationId);

        return ResponseEntity.ok(ApiResponse.<RecurringBillingResponseDto>builder()
                .success(true)
                .data(response)
                .build());
    }

    @GetMapping("/billing-configuration/{billingConfigurationId}")
    public ResponseEntity<ApiResponse<List<RecurringBillingResponseDto>>> getByBillingConfiguration(
            @PathVariable UUID billingConfigurationId) {

        List<RecurringBillingResponseDto> response =
                recurringBillingService.getByBillingConfiguration(billingConfigurationId);

        return ResponseEntity.ok(ApiResponse.<List<RecurringBillingResponseDto>>builder()
                .success(true)
                .data(response)
                .build());
    }

    @GetMapping("/{recurringConfigurationId}/schedule")
    public ResponseEntity<ApiResponse<List<BillingPeriodDto>>> getBillingSchedule(
            @PathVariable UUID recurringConfigurationId) {

        List<BillingPeriodDto> response =
                recurringBillingService.getBillingSchedule(recurringConfigurationId);

        return ResponseEntity.ok(
                ApiResponse.<List<BillingPeriodDto>>builder()
                        .success(true)
                        .message("Recurring billing schedule fetched successfully.")
                        .data(response)
                        .build()
        );
    }

    @DeleteMapping("/{recurringConfigurationId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID recurringConfigurationId) {

        recurringBillingService.delete(recurringConfigurationId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Recurring billing configuration deleted successfully.")
                .build());
    }

}
