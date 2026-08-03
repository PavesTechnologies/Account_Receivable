package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.projectbilling_config.ClientResponseDto;
import com.AccountReceivableManagement.dto.projectbilling_config.ProjectResponseDto;
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
}
