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
import com.AccountReceivableManagement.security.AnyFinanceRole;
import com.AccountReceivableManagement.security.ExecutiveOnly;
import com.AccountReceivableManagement.security.ManagerOnly;

@RestController
@RequestMapping("/api/billing-configurations")
@RequiredArgsConstructor

public class BillingConfigurationController {
    private final BillingConfigurationService billingConfigurationService;

    @ExecutiveOnly
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

    @ExecutiveOnly
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

    @ExecutiveOnly
    @PostMapping("/preview-schedule")
    public ResponseEntity<ApiResponse<List<BillingPeriodDto>>> previewSchedule(
            @Valid @RequestBody BillingSchedulePreviewRequest request) {

        List<BillingPeriodDto> response =
                billingConfigurationService.previewSchedule(request);

        return ResponseEntity.ok(
                ApiResponse.<List<BillingPeriodDto>>builder()
                        .success(true)
                        .message("Billing schedule preview generated successfully.")
                        .data(response)
                        .build());
    }

    @ExecutiveOnly
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

    @ManagerOnly
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

    @ExecutiveOnly
    @GetMapping("/clients")
    public ResponseEntity<ApiResponse<List<ClientResponseDto>>> getClients() {

        return ResponseEntity.ok(
                ApiResponse.<List<ClientResponseDto>>builder()
                        .success(true)
                        .message("Clients fetched successfully.")
                        .data(billingConfigurationService.getClients())
                        .build());
    }

    @AnyFinanceRole
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

    @AnyFinanceRole
    @GetMapping("/{billingConfigurationId}")
    public ResponseEntity<BillingConfigurationResponseDto> getBillingConfiguration(
            @PathVariable UUID billingConfigurationId) {

        return ResponseEntity.ok(
                billingConfigurationService.getBillingConfiguration(billingConfigurationId));
    }

    @AnyFinanceRole
    @GetMapping
    public ResponseEntity<List<BillingConfigurationResponseDto>> getAllBillingConfigurations() {

        return ResponseEntity.ok(
                billingConfigurationService.getAllBillingConfigurations());
    }

    @ManagerOnly
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

    @ManagerOnly
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


    @ExecutiveOnly
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
    
    @ExecutiveOnly
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

    @ExecutiveOnly
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

    @ExecutiveOnly
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

    @ManagerOnly
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

    @AnyFinanceRole
    @GetMapping("/available-projects")
    public ResponseEntity<ApiResponse<List<ProjectResponseDto>>> getAvailableProjectsForNewConfiguration(
            @RequestParam UUID clientId) {

        return ResponseEntity.ok(
                ApiResponse.<List<ProjectResponseDto>>builder()
                        .success(true)
                        .message("Available projects for new billing configuration fetched successfully.")
                        .data(billingConfigurationService.getAvailableProjectsForNewConfiguration(clientId))
                        .build()
        );
    }

}
