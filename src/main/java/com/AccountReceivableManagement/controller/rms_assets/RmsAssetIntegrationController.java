package com.AccountReceivableManagement.controller.rms_assets;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.rms_assets.ProjectBillableAssetResponseDto;
import com.AccountReceivableManagement.integration.rms_assets.RmsAssetIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only view onto RMS project asset assignments, for AR consumption only.
 * Today backed by {@code MockRmsAssetIntegrationService}; later this will
 * delegate to a real RMS integration. This contract (path, method, response
 * shape) stays stable across that swap.
 */
@RestController
@RequestMapping("/api/rms-assets")
@RequiredArgsConstructor
public class RmsAssetIntegrationController {

    private final RmsAssetIntegrationService rmsAssetIntegrationService;

    @GetMapping("/projects/{projectId}/billable-assets")
    public ResponseEntity<ApiResponse<List<ProjectBillableAssetResponseDto>>> getBillableAssets(
            @PathVariable Long projectId) {

        List<ProjectBillableAssetResponseDto> response =
                rmsAssetIntegrationService.getBillableAssetsForProject(projectId);

        return ResponseEntity.ok(
                ApiResponse.<List<ProjectBillableAssetResponseDto>>builder()
                        .success(true)
                        .message("Billable assets retrieved successfully.")
                        .data(response)
                        .build());
    }
}
