package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxRegimeRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxRegimeResponseDto;
import com.AccountReceivableManagement.service_interface.projectbilling_config.TaxRegimeMasterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tax-regime-master")
@RequiredArgsConstructor
public class TaxRegimeMasterController {

    private final TaxRegimeMasterService taxRegimeMasterService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaxRegimeResponseDto>> createTaxRegime(
            @Valid @RequestBody TaxRegimeRequestDto request
    ) {

        TaxRegimeResponseDto response =
                taxRegimeMasterService.createTaxRegime(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<TaxRegimeResponseDto>builder()
                        .success(true)
                        .message("Tax regime created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/{taxRegimeId}")
    public ResponseEntity<ApiResponse<TaxRegimeResponseDto>> updateTaxRegime(
            @PathVariable UUID taxRegimeId,
            @Valid @RequestBody TaxRegimeRequestDto request
    ) {

        TaxRegimeResponseDto response =
                taxRegimeMasterService.updateTaxRegime(
                        taxRegimeId,
                        request
                );

        return ResponseEntity.ok(
                ApiResponse.<TaxRegimeResponseDto>builder()
                        .success(true)
                        .message("Tax regime updated successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping("/{taxRegimeId}")
    public ResponseEntity<ApiResponse<TaxRegimeResponseDto>> getTaxRegimeById(
            @PathVariable UUID taxRegimeId
    ) {

        return ResponseEntity.ok(
                ApiResponse.<TaxRegimeResponseDto>builder()
                        .success(true)
                        .message("Tax regime retrieved successfully.")
                        .data(taxRegimeMasterService.getTaxRegimeById(taxRegimeId))
                        .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaxRegimeResponseDto>>> getAllTaxRegimes() {

        return ResponseEntity.ok(
                ApiResponse.<List<TaxRegimeResponseDto>>builder()
                        .success(true)
                        .message("Tax regimes retrieved successfully.")
                        .data(taxRegimeMasterService.getAllTaxRegimes())
                        .build());
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<TaxRegimeResponseDto>>> getActiveTaxRegimes() {

        return ResponseEntity.ok(
                ApiResponse.<List<TaxRegimeResponseDto>>builder()
                        .success(true)
                        .message("Active tax regimes retrieved successfully.")
                        .data(taxRegimeMasterService.getActiveTaxRegimes())
                        .build());
    }

    @PatchMapping("/{taxRegimeId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateTaxRegime(
            @PathVariable UUID taxRegimeId
    ) {

        taxRegimeMasterService.deactivateTaxRegime(taxRegimeId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Tax regime deactivated successfully.")
                        .data(null)
                        .build());
    }
}
