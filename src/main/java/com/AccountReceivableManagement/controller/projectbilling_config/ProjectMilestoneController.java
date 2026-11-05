package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.ProjectMilestoneResponseDto;
import com.AccountReceivableManagement.service_interface.projectbilling_config.ProjectMilestoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The PMS milestones synchronised into AR, for the billing-setup screens. Read-only: milestones are owned by PMS and
 * arrive through CDC; AR never edits them. Authentication is required like every /api/** endpoint.
 */
@RestController
@RequestMapping("/api/projects/{pmsProjectId}/milestones")
@RequiredArgsConstructor
public class ProjectMilestoneController {

    private final ProjectMilestoneService projectMilestoneService;

    /**
     * {@code GET /api/projects/{pmsProjectId}/milestones[?billingReadiness=READY_FOR_BILLING][&billableOnly=true]}
     * returns an empty list (not an error) for a known project that has no synchronised milestones yet, and 404 for a
     * project AR does not know.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectMilestoneResponseDto>>> getMilestones(
            @PathVariable Long pmsProjectId,
            @RequestParam(required = false) String billingReadiness,
            @RequestParam(defaultValue = "false") boolean billableOnly) {

        List<ProjectMilestoneResponseDto> milestones =
                projectMilestoneService.getMilestones(pmsProjectId, billingReadiness, billableOnly);

        return ResponseEntity.ok(
                ApiResponse.<List<ProjectMilestoneResponseDto>>builder()
                        .success(true)
                        .message(milestones.isEmpty()
                                ? "No milestones have been synchronised from PMS for this project yet."
                                : "Project milestones retrieved successfully.")
                        .data(milestones)
                        .build());
    }
}
