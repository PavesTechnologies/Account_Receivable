package com.AccountReceivableManagement.controller.tax_calculation;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.tax_calculation.TaxTypeRequestDto;
import com.AccountReceivableManagement.dto.tax_calculation.TaxTypeResponseDto;
import com.AccountReceivableManagement.service_interface.tax_calculation.TaxTypeMasterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tax-type-master")
@RequiredArgsConstructor
public class TaxTypeMasterController {

    private final TaxTypeMasterService taxTypeMasterService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaxTypeResponseDto>> createTaxType(
            @Valid @RequestBody TaxTypeRequestDto request
    ) {

        TaxTypeResponseDto response =
                taxTypeMasterService.createTaxType(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<TaxTypeResponseDto>builder()
                        .success(true)
                        .message("Tax type created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/{taxTypeId}")
    public ResponseEntity<ApiResponse<TaxTypeResponseDto>> updateTaxType(
            @PathVariable UUID taxTypeId,
            @Valid @RequestBody TaxTypeRequestDto request
    ) {

        TaxTypeResponseDto response =
                taxTypeMasterService.updateTaxType(
                        taxTypeId,
                        request
                );

        return ResponseEntity.ok(
                ApiResponse.<TaxTypeResponseDto>builder()
                        .success(true)
                        .message("Tax type updated successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping("/{taxTypeId}")
    public ResponseEntity<ApiResponse<TaxTypeResponseDto>> getTaxTypeById(
            @PathVariable UUID taxTypeId
    ) {

        TaxTypeResponseDto response =
                taxTypeMasterService.getTaxTypeById(taxTypeId);

        return ResponseEntity.ok(
                ApiResponse.<TaxTypeResponseDto>builder()
                        .success(true)
                        .message("Tax type retrieved successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaxTypeResponseDto>>> getAllTaxTypes() {

        List<TaxTypeResponseDto> response =
                taxTypeMasterService.getAllTaxTypes();

        return ResponseEntity.ok(
                ApiResponse.<List<TaxTypeResponseDto>>builder()
                        .success(true)
                        .message("Tax types retrieved successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<TaxTypeResponseDto>>> getActiveTaxTypes() {

        List<TaxTypeResponseDto> response =
                taxTypeMasterService.getActiveTaxTypes();

        return ResponseEntity.ok(
                ApiResponse.<List<TaxTypeResponseDto>>builder()
                        .success(true)
                        .message("Active tax types retrieved successfully.")
                        .data(response)
                        .build());
    }

    @PatchMapping("/{taxTypeId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateTaxType(
            @PathVariable UUID taxTypeId
    ) {

        taxTypeMasterService.deactivateTaxType(taxTypeId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Tax type deactivated successfully.")
                        .data(null)
                        .build());
    }
}
