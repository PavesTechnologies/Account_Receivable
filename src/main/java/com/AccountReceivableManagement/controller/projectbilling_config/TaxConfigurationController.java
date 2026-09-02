package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationResponseDto;
import com.AccountReceivableManagement.service_interface.projectbilling_config.TaxConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tax-rate-configurations")
@RequiredArgsConstructor
public class TaxConfigurationController {

    private final TaxConfigurationService taxRateConfigurationService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaxConfigurationResponseDto>> create(
            @Valid @RequestBody TaxConfigurationRequestDto request) {

        TaxConfigurationResponseDto response = taxRateConfigurationService.create(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<TaxConfigurationResponseDto>builder()
                        .success(true)
                        .message("Tax Rate Configuration created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/{taxRateConfigurationId}")
    public ResponseEntity<ApiResponse<TaxConfigurationResponseDto>> update(
            @PathVariable UUID taxRateConfigurationId,
            @Valid @RequestBody TaxConfigurationRequestDto request) {

        TaxConfigurationResponseDto response =
                taxRateConfigurationService.update(taxRateConfigurationId, request);

        return ResponseEntity.ok(ApiResponse.<TaxConfigurationResponseDto>builder()
                .success(true)
                .message("Tax Rate Configuration updated successfully.")
                .data(response)
                .build());
    }

    @GetMapping("/{taxRateConfigurationId}")
    public ResponseEntity<ApiResponse<TaxConfigurationResponseDto>> getById(
            @PathVariable UUID taxRateConfigurationId) {

        TaxConfigurationResponseDto response =
                taxRateConfigurationService.getById(taxRateConfigurationId);

        return ResponseEntity.ok(ApiResponse.<TaxConfigurationResponseDto>builder()
                .success(true)
                .message("Tax Rate Configuration retrieved successfully.")
                .data(response)
                .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaxConfigurationResponseDto>>> getAll() {

        List<TaxConfigurationResponseDto> response = taxRateConfigurationService.getAll();

        return ResponseEntity.ok(ApiResponse.<List<TaxConfigurationResponseDto>>builder()
                .success(true)
                .message("Tax Rate Configurations retrieved successfully.")
                .data(response)
                .build());
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<TaxConfigurationResponseDto>>> getActive() {

        List<TaxConfigurationResponseDto> response = taxRateConfigurationService.getActive();

        return ResponseEntity.ok(ApiResponse.<List<TaxConfigurationResponseDto>>builder()
                .success(true)
                .message("Active Tax Rate Configurations retrieved successfully.")
                .data(response)
                .build());
    }

    @GetMapping("/tax-region/{taxRegionId}")
    public ResponseEntity<ApiResponse<List<TaxConfigurationResponseDto>>> getByTaxRegion(
            @PathVariable UUID taxRegionId) {

        List<TaxConfigurationResponseDto> response =
                taxRateConfigurationService.getByTaxRegion(taxRegionId);

        return ResponseEntity.ok(ApiResponse.<List<TaxConfigurationResponseDto>>builder()
                .success(true)
                .message("Tax Rate Configurations retrieved successfully.")
                .data(response)
                .build());
    }

    @PatchMapping("/{taxRateConfigurationId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @PathVariable UUID taxRateConfigurationId) {

        taxRateConfigurationService.deactivate(taxRateConfigurationId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Tax Rate Configuration deactivated successfully.")
                .data(null)
                .build());
    }
}
