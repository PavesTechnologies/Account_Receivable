package com.AccountReceivableManagement.service_interface.project_tool_assignment;

import com.AccountReceivableManagement.dto.project_tool_assignment.ProjectToolAssignmentRenewalRequestDto;
import com.AccountReceivableManagement.dto.project_tool_assignment.ProjectToolAssignmentRequestDto;
import com.AccountReceivableManagement.dto.project_tool_assignment.ProjectToolAssignmentResponseDto;

import java.util.List;
import java.util.UUID;

public interface ProjectToolAssignmentService {

    ProjectToolAssignmentResponseDto create(ProjectToolAssignmentRequestDto request);

    ProjectToolAssignmentResponseDto update(UUID assignmentId, ProjectToolAssignmentRequestDto request);

    ProjectToolAssignmentResponseDto getById(UUID assignmentId);

    List<ProjectToolAssignmentResponseDto> getByProject(Long projectId);

    List<ProjectToolAssignmentResponseDto> getAll();

    void delete(UUID assignmentId);

    ProjectToolAssignmentResponseDto renew(UUID assignmentId, ProjectToolAssignmentRenewalRequestDto request);
}
