package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxStructureResponseDto;
import com.AccountReceivableManagement.service_interface.projectbilling_config.TaxStructureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for tax structure discovery API.
 * Allows frontend to dynamically discover available tax regimes and components
 * for a given tax region without hardcoding country-specific logic.
 */
@RestController
@RequestMapping("/api/tax-structure")
@RequiredArgsConstructor
public class TaxStructureController {

    private final TaxStructureService taxStructureService;

    @GetMapping("/regions/{taxRegionId}")
    public ResponseEntity<ApiResponse<TaxStructureResponseDto>> getTaxStructureByRegion(
            @PathVariable UUID taxRegionId
    ) {

        TaxStructureResponseDto response =
                taxStructureService.getTaxStructureByRegion(taxRegionId);

        return ResponseEntity.ok(
                ApiResponse.<TaxStructureResponseDto>builder()
                        .success(true)
                        .message("Tax structure retrieved successfully.")
                        .data(response)
                        .build());
    }
}
