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
import com.AccountReceivableManagement.security.ExecutiveOnly;

@RestController
@RequestMapping("/api/tax-region")
@RequiredArgsConstructor
public class TaxRegionMasterController {

    private final TaxRegionMasterService taxRegionService;

    @ExecutiveOnly
    @PostMapping
    public ResponseEntity<ApiResponse<TaxRegionResponseDto>> create(
            @Valid @RequestBody TaxRegionRequestDto request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<TaxRegionResponseDto>builder()
                        .success(true)
                        .message("Tax Region created successfully.")
                        .data(taxRegionService.createTaxRegion(request))
                        .build());
    }

    @ExecutiveOnly
    @PutMapping("/{taxRegionId}")
    public ResponseEntity<ApiResponse<TaxRegionResponseDto>> update(
            @PathVariable UUID taxRegionId,
            @Valid @RequestBody TaxRegionRequestDto request) {

        return ResponseEntity.ok(
                ApiResponse.<TaxRegionResponseDto>builder()
                        .success(true)
                        .message("Tax Region updated successfully.")
                        .data(taxRegionService.updateTaxRegion(
                                taxRegionId,
                                request
                        ))
                        .build()
        );
    }

    @ExecutiveOnly
    @GetMapping("/{taxRegionId}")
    public ResponseEntity<ApiResponse<TaxRegionResponseDto>> getById(
            @PathVariable UUID taxRegionId) {

        return ResponseEntity.ok(
                ApiResponse.<TaxRegionResponseDto>builder()
                        .success(true)
                        .message("Tax Region retrieved successfully.")
                        .data(taxRegionService.getTaxRegionById(taxRegionId))
                        .build()
        );
    }

    @ExecutiveOnly
    @GetMapping
    public ResponseEntity<ApiResponse<List<TaxRegionResponseDto>>> getAll() {

        return ResponseEntity.ok(
                ApiResponse.<List<TaxRegionResponseDto>>builder()
                        .success(true)
                        .message("Tax Regions retrieved successfully.")
                        .data(taxRegionService.getAllTaxRegions())
                        .build()
        );
    }

    @ExecutiveOnly
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<TaxRegionResponseDto>>> getActive() {

        return ResponseEntity.ok(
                ApiResponse.<List<TaxRegionResponseDto>>builder()
                        .success(true)
                        .message("Active Tax Regions retrieved successfully.")
                        .data(taxRegionService.getActiveTaxRegions())
                        .build()
        );
    }

    @ExecutiveOnly
    @PatchMapping("/{taxRegionId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @PathVariable UUID taxRegionId) {

        taxRegionService.deactivateTaxRegion(taxRegionId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Tax Region deactivated successfully.")
                        .data(null)
                        .build()
        );
    }
}
