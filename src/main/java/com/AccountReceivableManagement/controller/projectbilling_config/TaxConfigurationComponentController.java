package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationComponentRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationComponentResponseDto;
import com.AccountReceivableManagement.service_interface.projectbilling_config.TaxConfigurationComponentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tax-configuration-components")
@RequiredArgsConstructor
public class TaxConfigurationComponentController {

    private final TaxConfigurationComponentService componentService;

    @PostMapping("/tax-configuration/{taxConfigurationId}")
    public ResponseEntity<ApiResponse<TaxConfigurationComponentResponseDto>>
    createComponent(
            @PathVariable UUID taxConfigurationId,
            @Valid @RequestBody TaxConfigurationComponentRequestDto request
    ) {

        TaxConfigurationComponentResponseDto response =
                componentService.createComponent(
                        taxConfigurationId,
                        request
                );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<TaxConfigurationComponentResponseDto>builder()
                        .success(true)
                        .message("Tax configuration component created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/{taxConfigurationComponentId}")
    public ResponseEntity<ApiResponse<TaxConfigurationComponentResponseDto>>
    updateComponent(
            @PathVariable UUID taxConfigurationComponentId,
            @Valid @RequestBody TaxConfigurationComponentRequestDto request
    ) {

        TaxConfigurationComponentResponseDto response =
                componentService.updateComponent(
                        taxConfigurationComponentId,
                        request
                );

        return ResponseEntity.ok(
                ApiResponse.<TaxConfigurationComponentResponseDto>builder()
                        .success(true)
                        .message("Tax configuration component updated successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping("/{taxConfigurationComponentId}")
    public ResponseEntity<ApiResponse<TaxConfigurationComponentResponseDto>>
    getComponentById(
            @PathVariable UUID taxConfigurationComponentId
    ) {

        TaxConfigurationComponentResponseDto response =
                componentService.getComponentById(
                        taxConfigurationComponentId
                );

        return ResponseEntity.ok(
                ApiResponse.<TaxConfigurationComponentResponseDto>builder()
                        .success(true)
                        .message("Tax configuration component retrieved successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping("/tax-configuration/{taxConfigurationId}")
    public ResponseEntity<
            ApiResponse<List<TaxConfigurationComponentResponseDto>>
            >
    getComponentsByConfiguration(
            @PathVariable UUID taxConfigurationId
    ) {

        List<TaxConfigurationComponentResponseDto> response =
                componentService.getComponentsByConfiguration(
                        taxConfigurationId
                );

        return ResponseEntity.ok(
                ApiResponse.<List<TaxConfigurationComponentResponseDto>>builder()
                        .success(true)
                        .message("Tax configuration components retrieved successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping
    public ResponseEntity<
            ApiResponse<List<TaxConfigurationComponentResponseDto>>
            >
    getAllComponents() {

        List<TaxConfigurationComponentResponseDto> response =
                componentService.getAllComponents();

        return ResponseEntity.ok(
                ApiResponse.<List<TaxConfigurationComponentResponseDto>>builder()
                        .success(true)
                        .message("Tax configuration components retrieved successfully.")
                        .data(response)
                        .build());
    }

    @PatchMapping("/{taxConfigurationComponentId}/deactivate")
    public ResponseEntity<ApiResponse<Void>>
    deactivateComponent(
            @PathVariable UUID taxConfigurationComponentId
    ) {

        componentService.deactivateComponent(
                taxConfigurationComponentId
        );

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Tax configuration component deactivated successfully.")
                        .data(null)
                        .build());
    }
}
