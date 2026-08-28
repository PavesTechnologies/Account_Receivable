package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.*;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/billing-configurations")
@RequiredArgsConstructor

public class BillingConfigurationController {
    private final BillingConfigurationService billingConfigurationService;

    @PostMapping
    public ResponseEntity<ApiResponse<BillingConfigurationResponseDto>> create(
            @Valid @RequestBody BillingConfigurationRequestDto request) {

        BillingConfigurationResponseDto response =
                billingConfigurationService.create(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<BillingConfigurationResponseDto>builder()
                        .success(true)
                        .message("Billing configuration created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/{billingConfigurationId}")
    public ResponseEntity<ApiResponse<BillingConfigurationResponseDto>> update(
            @PathVariable UUID billingConfigurationId,
            @Valid @RequestBody BillingConfigurationRequestDto request) {

        BillingConfigurationResponseDto response =
                billingConfigurationService.updateBillingConfiguration(billingConfigurationId, request);

        return ResponseEntity.ok(ApiResponse.<BillingConfigurationResponseDto>builder()
                .success(true)
                .message("Billing configuration updated successfully.")
                .data(response)
                .build());
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<BillingConfigurationResponseDto>> getApprovedByProjectId(
            @PathVariable Long projectId) {

        BillingConfigurationResponseDto response =
                billingConfigurationService.getApprovedByProjectId(projectId);

        return ResponseEntity.ok(ApiResponse.<BillingConfigurationResponseDto>builder()
                .success(true)
                .message("Billing configuration retrieved successfully.")
                .data(response)
                .build());
    }

    @PutMapping("/{billingConfigurationId}/approve")
    public ResponseEntity<ApiResponse<BillingConfigurationResponseDto>> approve(
            @PathVariable UUID billingConfigurationId) {

        return ResponseEntity.ok(
                ApiResponse.<BillingConfigurationResponseDto>builder()
                        .success(true)
                        .message("Billing Configuration approved successfully.")
                        .data(billingConfigurationService.approve(billingConfigurationId))
                        .build());
    }

    @GetMapping("/clients")
    public ResponseEntity<ApiResponse<List<ClientResponseDto>>> getClients() {

        return ResponseEntity.ok(
                ApiResponse.<List<ClientResponseDto>>builder()
                        .success(true)
                        .message("Clients fetched successfully.")
                        .data(billingConfigurationService.getClients())
                        .build());
    }

    @GetMapping("/projects/{clientId}")
    public ResponseEntity<ApiResponse<List<ProjectResponseDto>>> getProjects(
            @PathVariable UUID clientId) {

        return ResponseEntity.ok(
                ApiResponse.<List<ProjectResponseDto>>builder()
                        .success(true)
                        .message("Projects fetched successfully.")
                        .data(billingConfigurationService.getProjects(clientId))
                        .build());
    }

    @GetMapping("/{billingConfigurationId}")
    public ResponseEntity<BillingConfigurationResponseDto> getBillingConfiguration(
            @PathVariable UUID billingConfigurationId) {

        return ResponseEntity.ok(
                billingConfigurationService.getBillingConfiguration(billingConfigurationId));
    }

    @GetMapping
    public ResponseEntity<List<BillingConfigurationResponseDto>> getAllBillingConfigurations() {

        return ResponseEntity.ok(
                billingConfigurationService.getAllBillingConfigurations());
    }

    @PatchMapping("/{billingConfigurationId}/deactivate")
    public ResponseEntity<ApiResponse<String>> deactivateBillingConfiguration(
            @PathVariable UUID billingConfigurationId) {

        billingConfigurationService
                .deactivateBillingConfiguration(billingConfigurationId);

        return ResponseEntity.ok(
                ApiResponse.<String>builder()
                        .success(true)
                        .message("Billing Configuration deactivated successfully.")
                        .data("SUCCESS")
                        .build());
    }

    @PutMapping("/{billingConfigurationId}/reject")
    public ResponseEntity<ApiResponse<BillingConfigurationResponseDto>> reject(
            @PathVariable UUID billingConfigurationId,
            @Valid @RequestBody BillingConfigurationRejectRequestDto request) {

        return ResponseEntity.ok(
                ApiResponse.<BillingConfigurationResponseDto>builder()
                        .success(true)
                        .message("Billing Configuration rejected successfully.")
                        .data(
                                billingConfigurationService.reject(
                                        billingConfigurationId,
                                        request))
                        .build());
    }


    @DeleteMapping("/{billingConfigurationId}")
    public ResponseEntity<ApiResponse<Void>> deleteBillingConfiguration(
            @PathVariable UUID billingConfigurationId) {

        billingConfigurationService.deleteBillingConfiguration(
                billingConfigurationId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Billing Configuration deleted successfully.")
                        .build()
        );
    }
    
    @PostMapping("/draft")
    public ResponseEntity<ApiResponse<BillingConfigurationDraftResponseDto>> createDraft(
            @Valid @RequestBody BillingConfigurationDraftRequestDto request) {

        BillingConfigurationDraftResponseDto response =
                billingConfigurationService.createDraft(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.<BillingConfigurationDraftResponseDto>builder()
                                .success(true)
                                .message(
                                        "Billing configuration draft initialized successfully.")
                                .data(response)
                                .build()
                );
    }

    @PutMapping("/{billingConfigurationId}/draft")
    public ResponseEntity<ApiResponse<BillingConfigurationDraftResponseDto>> saveDraft(
            @PathVariable UUID billingConfigurationId,
            @Valid @RequestBody BillingConfigurationDraftRequestDto request) {

        BillingConfigurationDraftResponseDto response =
                billingConfigurationService.saveDraft(
                        billingConfigurationId,
                        request);

        return ResponseEntity.ok(
                ApiResponse.<BillingConfigurationDraftResponseDto>builder()
                        .success(true)
                        .message("Billing configuration draft saved successfully.")
                        .data(response)
                        .build()
        );
    }

    @PutMapping("/{billingConfigurationId}/submit")
    public ResponseEntity<ApiResponse<BillingConfigurationResponseDto>>
    submitForApproval(
            @PathVariable UUID billingConfigurationId) {

        return ResponseEntity.ok(
                ApiResponse.<BillingConfigurationResponseDto>builder()
                        .success(true)
                        .message(
                                "Billing Configuration submitted for approval successfully.")
                        .data(
                                billingConfigurationService
                                        .submitForApproval(
                                                billingConfigurationId))
                        .build()
        );
    }

    @GetMapping("/pending-approvals")
    public ResponseEntity<ApiResponse<List<BillingConfigurationResponseDto>>>
    getPendingApprovals() {

        return ResponseEntity.ok(
                ApiResponse.<List<BillingConfigurationResponseDto>>builder()
                        .success(true)
                        .message("Pending billing configuration approvals fetched successfully.")
                        .data(billingConfigurationService.getPendingApprovals())
                        .build()
        );
    }

}
