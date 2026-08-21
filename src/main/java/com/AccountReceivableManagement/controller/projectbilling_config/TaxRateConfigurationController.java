package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxRateConfigurationRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxRateConfigurationResponseDto;
import com.AccountReceivableManagement.service_interface.projectbilling_config.TaxRateConfigurationService;
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
public class TaxRateConfigurationController {

    private final TaxRateConfigurationService taxRateConfigurationService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaxRateConfigurationResponseDto>> create(
            @Valid @RequestBody TaxRateConfigurationRequestDto request) {

        TaxRateConfigurationResponseDto response = taxRateConfigurationService.create(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<TaxRateConfigurationResponseDto>builder()
                        .success(true)
                        .message("Tax Rate Configuration created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/{taxRateConfigurationId}")
    public ResponseEntity<ApiResponse<TaxRateConfigurationResponseDto>> update(
            @PathVariable UUID taxRateConfigurationId,
            @Valid @RequestBody TaxRateConfigurationRequestDto request) {

        TaxRateConfigurationResponseDto response =
                taxRateConfigurationService.update(taxRateConfigurationId, request);

        return ResponseEntity.ok(ApiResponse.<TaxRateConfigurationResponseDto>builder()
                .success(true)
                .message("Tax Rate Configuration updated successfully.")
                .data(response)
                .build());
    }

    @GetMapping("/{taxRateConfigurationId}")
    public ResponseEntity<ApiResponse<TaxRateConfigurationResponseDto>> getById(
            @PathVariable UUID taxRateConfigurationId) {

        TaxRateConfigurationResponseDto response =
                taxRateConfigurationService.getById(taxRateConfigurationId);

        return ResponseEntity.ok(ApiResponse.<TaxRateConfigurationResponseDto>builder()
                .success(true)
                .message("Tax Rate Configuration retrieved successfully.")
                .data(response)
                .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaxRateConfigurationResponseDto>>> getAll() {

        List<TaxRateConfigurationResponseDto> response = taxRateConfigurationService.getAll();

        return ResponseEntity.ok(ApiResponse.<List<TaxRateConfigurationResponseDto>>builder()
                .success(true)
                .message("Tax Rate Configurations retrieved successfully.")
                .data(response)
                .build());
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<TaxRateConfigurationResponseDto>>> getActive() {

        List<TaxRateConfigurationResponseDto> response = taxRateConfigurationService.getActive();

        return ResponseEntity.ok(ApiResponse.<List<TaxRateConfigurationResponseDto>>builder()
                .success(true)
                .message("Active Tax Rate Configurations retrieved successfully.")
                .data(response)
                .build());
    }

    @GetMapping("/tax-region/{taxRegionId}")
    public ResponseEntity<ApiResponse<List<TaxRateConfigurationResponseDto>>> getByTaxRegion(
            @PathVariable UUID taxRegionId) {

        List<TaxRateConfigurationResponseDto> response =
                taxRateConfigurationService.getByTaxRegion(taxRegionId);

        return ResponseEntity.ok(ApiResponse.<List<TaxRateConfigurationResponseDto>>builder()
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
