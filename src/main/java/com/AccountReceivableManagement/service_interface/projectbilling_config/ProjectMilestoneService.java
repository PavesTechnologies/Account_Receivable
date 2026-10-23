package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.ProjectMilestoneResponseDto;

import java.util.List;

public interface ProjectMilestoneService {

    /**
     * The non-deleted milestones PMS has synchronised for a project, oldest PMS id first.
     *
     * @param pmsProjectId     the PMS project id (the same value AR uses as its project id)
     * @param billingReadiness optional filter: NOT_BILLABLE, NOT_READY or READY_FOR_BILLING (case-insensitive)
     * @param billableOnly     when true, only milestones flagged billable
     * @throws com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler.ResourceNotFoundException
     *         if AR does not know the project
     * @throws com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler.ValidationException
     *         if {@code billingReadiness} is not a known value
     */
    List<ProjectMilestoneResponseDto> getMilestones(Long pmsProjectId, String billingReadiness, boolean billableOnly);
}
