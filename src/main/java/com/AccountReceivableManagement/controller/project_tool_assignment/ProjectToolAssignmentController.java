package com.AccountReceivableManagement.controller.project_tool_assignment;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.project_tool_assignment.ProjectToolAssignmentRenewalRequestDto;
import com.AccountReceivableManagement.dto.project_tool_assignment.ProjectToolAssignmentRequestDto;
import com.AccountReceivableManagement.dto.project_tool_assignment.ProjectToolAssignmentResponseDto;
import com.AccountReceivableManagement.service_interface.project_tool_assignment.ProjectToolAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/project-tool-assignments")
@RequiredArgsConstructor
public class ProjectToolAssignmentController {

    private final ProjectToolAssignmentService projectToolAssignmentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectToolAssignmentResponseDto>>> getAll() {

        List<ProjectToolAssignmentResponseDto> response = projectToolAssignmentService.getAll();

        return ResponseEntity.ok(
                ApiResponse.<List<ProjectToolAssignmentResponseDto>>builder()
                        .success(true)
                        .message("Project Tool Assignments retrieved successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<List<ProjectToolAssignmentResponseDto>>> getByProject(
            @PathVariable Long projectId) {

        List<ProjectToolAssignmentResponseDto> response = projectToolAssignmentService.getByProject(projectId);

        return ResponseEntity.ok(
                ApiResponse.<List<ProjectToolAssignmentResponseDto>>builder()
                        .success(true)
                        .message("Project Tool Assignments retrieved successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectToolAssignmentResponseDto>> getById(
            @PathVariable UUID id) {

        ProjectToolAssignmentResponseDto response = projectToolAssignmentService.getById(id);

        return ResponseEntity.ok(
                ApiResponse.<ProjectToolAssignmentResponseDto>builder()
                        .success(true)
                        .message("Project Tool Assignment retrieved successfully.")
                        .data(response)
                        .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectToolAssignmentResponseDto>> create(
            @Valid @RequestBody ProjectToolAssignmentRequestDto request) {

        ProjectToolAssignmentResponseDto response = projectToolAssignmentService.create(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<ProjectToolAssignmentResponseDto>builder()
                        .success(true)
                        .message("Project Tool Assignment created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectToolAssignmentResponseDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectToolAssignmentRequestDto request) {

        ProjectToolAssignmentResponseDto response = projectToolAssignmentService.update(id, request);

        return ResponseEntity.ok(
                ApiResponse.<ProjectToolAssignmentResponseDto>builder()
                        .success(true)
                        .message("Project Tool Assignment updated successfully.")
                        .data(response)
                        .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id) {

        projectToolAssignmentService.delete(id);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Project Tool Assignment deleted successfully.")
                        .data(null)
                        .build());
    }

    @PostMapping("/{id}/renew")
    public ResponseEntity<ApiResponse<ProjectToolAssignmentResponseDto>> renew(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectToolAssignmentRenewalRequestDto request) {

        ProjectToolAssignmentResponseDto response = projectToolAssignmentService.renew(id, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<ProjectToolAssignmentResponseDto>builder()
                        .success(true)
                        .message("Project Tool Assignment renewed successfully.")
                        .data(response)
                        .build());
    }
}
