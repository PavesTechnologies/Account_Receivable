package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxComponentRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxComponentResponseDto;
import com.AccountReceivableManagement.service_interface.projectbilling_config.TaxComponentMasterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tax-component-master")
@RequiredArgsConstructor
public class TaxComponentMasterController {

    private final TaxComponentMasterService taxComponentMasterService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaxComponentResponseDto>> createTaxComponent(
            @Valid @RequestBody TaxComponentRequestDto request
    ) {

        TaxComponentResponseDto response =
                taxComponentMasterService.createTaxComponent(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<TaxComponentResponseDto>builder()
                        .success(true)
                        .message("Tax component created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/{taxComponentId}")
    public ResponseEntity<ApiResponse<TaxComponentResponseDto>> updateTaxComponent(
            @PathVariable UUID taxComponentId,
            @Valid @RequestBody TaxComponentRequestDto request
    ) {

        TaxComponentResponseDto response =
                taxComponentMasterService.updateTaxComponent(
                        taxComponentId,
                        request
                );

        return ResponseEntity.ok(
                ApiResponse.<TaxComponentResponseDto>builder()
                        .success(true)
                        .message("Tax component updated successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping("/{taxComponentId}")
    public ResponseEntity<ApiResponse<TaxComponentResponseDto>> getTaxComponentById(
            @PathVariable UUID taxComponentId
    ) {

        return ResponseEntity.ok(
                ApiResponse.<TaxComponentResponseDto>builder()
                        .success(true)
                        .message("Tax component retrieved successfully.")
                        .data(taxComponentMasterService.getTaxComponentById(taxComponentId))
                        .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaxComponentResponseDto>>> getAllTaxComponents() {

        return ResponseEntity.ok(
                ApiResponse.<List<TaxComponentResponseDto>>builder()
                        .success(true)
                        .message("Tax components retrieved successfully.")
                        .data(taxComponentMasterService.getAllTaxComponents())
                        .build());
    }

    @GetMapping("/regime/{taxRegimeId}")
    public ResponseEntity<ApiResponse<List<TaxComponentResponseDto>>> getTaxComponentsByRegime(
            @PathVariable UUID taxRegimeId
    ) {

        return ResponseEntity.ok(
                ApiResponse.<List<TaxComponentResponseDto>>builder()
                        .success(true)
                        .message("Tax components retrieved successfully.")
                        .data(taxComponentMasterService.getTaxComponentsByRegime(taxRegimeId))
                        .build());
    }

    @PatchMapping("/{taxComponentId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateTaxComponent(
            @PathVariable UUID taxComponentId
    ) {

        taxComponentMasterService.deactivateTaxComponent(taxComponentId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Tax component deactivated successfully.")
                        .data(null)
                        .build());
    }
}
