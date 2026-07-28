package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxRegionRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxRegionResponseDto;
import com.AccountReceivableManagement.service_interface.projectbilling_config.TaxRegionMasterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tax-region")
@RequiredArgsConstructor
public class TaxRegionMasterController {

    private final TaxRegionMasterService taxRegionService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaxRegionResponseDto>> createTaxRegion(
            @Valid @RequestBody TaxRegionRequestDto request) {

        TaxRegionResponseDto response = taxRegionService.createTaxRegion(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<TaxRegionResponseDto>builder()
                        .success(true)
                        .message("Tax Region created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/{taxRegionId}")
    public ResponseEntity<ApiResponse<TaxRegionResponseDto>> updateTaxRegion(
            @PathVariable UUID taxRegionId,
            @Valid @RequestBody TaxRegionRequestDto request) {

        TaxRegionResponseDto response =
                taxRegionService.updateTaxRegion(taxRegionId, request);

        return ResponseEntity.ok(ApiResponse.<TaxRegionResponseDto>builder()
                .success(true)
                .message("Tax Region updated successfully.")
                .data(response)
                .build());
    }

    @GetMapping("/{taxRegionId}")
    public ResponseEntity<ApiResponse<TaxRegionResponseDto>> getTaxRegionById(
            @PathVariable UUID taxRegionId) {

        TaxRegionResponseDto response =
                taxRegionService.getTaxRegionById(taxRegionId);

        return ResponseEntity.ok(ApiResponse.<TaxRegionResponseDto>builder()
                .success(true)
                .message("Tax Region retrieved successfully.")
                .data(response)
                .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaxRegionResponseDto>>> getAllTaxRegions() {

        List<TaxRegionResponseDto> response =
                taxRegionService.getAllTaxRegions();

        return ResponseEntity.ok(ApiResponse.<List<TaxRegionResponseDto>>builder()
                .success(true)
                .message("Tax Regions retrieved successfully.")
                .data(response)
                .build());
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<TaxRegionResponseDto>>> getActiveTaxRegions() {

        List<TaxRegionResponseDto> response =
                taxRegionService.getActiveTaxRegions();

        return ResponseEntity.ok(ApiResponse.<List<TaxRegionResponseDto>>builder()
                .success(true)
                .message("Active Tax Regions retrieved successfully.")
                .data(response)
                .build());
    }

    @DeleteMapping("/{taxRegionId}")
    public ResponseEntity<ApiResponse<Void>> deleteTaxRegion(
            @PathVariable UUID taxRegionId) {

        taxRegionService.deleteTaxRegion(taxRegionId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Tax Region deleted successfully.")
                .data(null)
                .build());
    }
}
