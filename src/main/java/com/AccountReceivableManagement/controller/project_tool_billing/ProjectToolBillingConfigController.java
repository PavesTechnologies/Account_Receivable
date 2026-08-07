package com.AccountReceivableManagement.controller.project_tool_billing;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.project_tool_billing.ProjectToolBillingConfigRequestDto;
import com.AccountReceivableManagement.dto.project_tool_billing.ProjectToolBillingConfigResponseDto;
import com.AccountReceivableManagement.service_interface.project_tool_billing.ProjectToolBillingConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ar/projects/{projectId}/tool-billing")
@RequiredArgsConstructor
public class ProjectToolBillingConfigController {

    private final ProjectToolBillingConfigService projectToolBillingConfigService;

    @GetMapping
    public ResponseEntity<ApiResponse<ProjectToolBillingConfigResponseDto>> getToolBillingConfig(
            @PathVariable Long projectId) {

        ProjectToolBillingConfigResponseDto response =
                projectToolBillingConfigService.getByProjectId(projectId);

        return ResponseEntity.ok(
                ApiResponse.<ProjectToolBillingConfigResponseDto>builder()
                        .success(true)
                        .message("Tool Billing Configuration fetched successfully.")
                        .data(response)
                        .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectToolBillingConfigResponseDto>> createToolBillingConfig(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectToolBillingConfigRequestDto request) {

        ProjectToolBillingConfigResponseDto response =
                projectToolBillingConfigService.save(projectId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<ProjectToolBillingConfigResponseDto>builder()
                        .success(true)
                        .message("Tool Billing Configuration created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ProjectToolBillingConfigResponseDto>> updateToolBillingConfig(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectToolBillingConfigRequestDto request) {

        ProjectToolBillingConfigResponseDto response =
                projectToolBillingConfigService.update(projectId, request);

        return ResponseEntity.ok(
                ApiResponse.<ProjectToolBillingConfigResponseDto>builder()
                        .success(true)
                        .message("Tool Billing Configuration updated successfully.")
                        .data(response)
                        .build());
    }
}
