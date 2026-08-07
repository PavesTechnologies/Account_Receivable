package com.AccountReceivableManagement.service_Imple.project_tool_assignment;

import com.AccountReceivableManagement.dto.project_tool_assignment.ProjectToolAssignmentRenewalRequestDto;
import com.AccountReceivableManagement.dto.project_tool_assignment.ProjectToolAssignmentRequestDto;
import com.AccountReceivableManagement.dto.project_tool_assignment.ProjectToolAssignmentResponseDto;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.project_tool_assignment.ProjectToolAssignment;
import com.AccountReceivableManagement.entity.projectbilling_config.CurrencyMaster;
import com.AccountReceivableManagement.entity.tool_catalog.ToolCatalog;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotItemRepository;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import com.AccountReceivableManagement.repo.project_tool_assignment.ProjectToolAssignmentRepository;
import com.AccountReceivableManagement.repo.tool_catalog.ToolCatalogRepository;
import com.AccountReceivableManagement.service_interface.project_tool_assignment.ProjectToolAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectToolAssignmentServiceImpl implements ProjectToolAssignmentService {

    private final ProjectToolAssignmentRepository projectToolAssignmentRepository;
    private final ProjectMasterReferenceRepository projectMasterReferenceRepository;
    private final ToolCatalogRepository toolCatalogRepository;
    private final BillingSnapshotItemRepository billingSnapshotItemRepository;

    @Override
    public ProjectToolAssignmentResponseDto create(ProjectToolAssignmentRequestDto request) {

        ProjectMasterReference project = projectMasterReferenceRepository.findById(request.getProjectId())
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Project not found."));

        ToolCatalog tool = toolCatalogRepository.findById(request.getToolId())
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Tool not found."));

        validateToolActive(tool);
        validateQuantity(request.getQuantity());
        validateDates(request.getStartDate(), request.getEndDate());
        validateNoOverlap(request.getProjectId(), request.getToolId(),
                request.getStartDate(), request.getEndDate(), null);

        ProjectToolAssignment assignment = ProjectToolAssignment.builder()
                .project(project)
                .tool(tool)
                .quantity(request.getQuantity())
                .remarks(request.getRemarks())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .isActive(true)
                .build();

        ProjectToolAssignment saved = projectToolAssignmentRepository.save(assignment);

        return mapToResponse(saved);
    }

    @Override
    public ProjectToolAssignmentResponseDto update(UUID assignmentId, ProjectToolAssignmentRequestDto request) {

        ProjectToolAssignment assignment = projectToolAssignmentRepository.findById(assignmentId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Assignment not found."));

        ProjectMasterReference project = projectMasterReferenceRepository.findById(request.getProjectId())
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Project not found."));

        ToolCatalog tool = toolCatalogRepository.findById(request.getToolId())
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Tool not found."));

        validateToolActive(tool);
        validateQuantity(request.getQuantity());
        validateDates(request.getStartDate(), request.getEndDate());
        validateNoOverlap(request.getProjectId(), request.getToolId(),
                request.getStartDate(), request.getEndDate(), assignmentId);

        assignment.setProject(project);
        assignment.setTool(tool);
        assignment.setQuantity(request.getQuantity());
        assignment.setRemarks(request.getRemarks());
        assignment.setStartDate(request.getStartDate());
        assignment.setEndDate(request.getEndDate());

        ProjectToolAssignment updated = projectToolAssignmentRepository.save(assignment);

        return mapToResponse(updated);
    }

    @Override
    public ProjectToolAssignmentResponseDto getById(UUID assignmentId) {

        ProjectToolAssignment assignment = projectToolAssignmentRepository.findById(assignmentId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Assignment not found."));

        return mapToResponse(assignment);
    }

    @Override
    public List<ProjectToolAssignmentResponseDto> getByProject(Long projectId) {

        return projectToolAssignmentRepository.findByProjectId(projectId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProjectToolAssignmentResponseDto> getAll() {

        return projectToolAssignmentRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(UUID assignmentId) {

        ProjectToolAssignment assignment = projectToolAssignmentRepository.findById(assignmentId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Assignment not found."));

        if (billingSnapshotItemRepository.existsBySourceReferenceId(assignmentId.toString())) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Cannot deactivate an assignment that has already contributed to a Billing Snapshot.");
        }

        assignment.setIsActive(false);

        projectToolAssignmentRepository.save(assignment);
    }

    @Override
    public ProjectToolAssignmentResponseDto renew(UUID assignmentId, ProjectToolAssignmentRenewalRequestDto request) {

        ProjectToolAssignment previous = projectToolAssignmentRepository.findById(assignmentId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Assignment not found."));

        if (previous.getEndDate() == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Cannot renew an assignment that has no End Date.");
        }

        validateDates(request.getStartDate(), request.getEndDate());

        if (!request.getStartDate().isAfter(previous.getEndDate())) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Renewed assignment must begin after the previous assignment's End Date.");
        }

        ToolCatalog tool = previous.getTool();
        validateToolActive(tool);

        Long projectId = previous.getProject().getPmsProjectId();
        UUID toolId = tool.getToolId();

        validateNoOverlap(projectId, toolId, request.getStartDate(), request.getEndDate(), null);

        ProjectToolAssignment renewed = ProjectToolAssignment.builder()
                .project(previous.getProject())
                .tool(tool)
                .quantity(previous.getQuantity())
                .remarks(previous.getRemarks())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .isActive(true)
                .build();

        ProjectToolAssignment saved = projectToolAssignmentRepository.save(renewed);

        return mapToResponse(saved);
    }

    private void validateToolActive(ToolCatalog tool) {

        if (!Boolean.TRUE.equals(tool.getIsActive())) {
            throw new GlobalExceptionHandler.ValidationException("Selected Tool is inactive.");
        }
    }

    private void validateQuantity(Integer quantity) {

        if (quantity == null || quantity <= 0) {
            throw new GlobalExceptionHandler.ValidationException("Quantity must be greater than zero.");
        }
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {

        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new GlobalExceptionHandler.ValidationException(
                    "End Date cannot be earlier than Start Date.");
        }
    }

    private void validateNoOverlap(Long projectId, UUID toolId, LocalDate startDate, LocalDate endDate,
                                    UUID excludeAssignmentId) {

        boolean overlaps = projectToolAssignmentRepository.existsOverlappingAssignment(
                projectId, toolId, startDate, endDate, excludeAssignmentId);

        if (overlaps) {
            throw new GlobalExceptionHandler.DuplicateResourceException(
                    "An active assignment for this Project and Tool already exists for an overlapping period.");
        }
    }

    private ProjectToolAssignmentResponseDto mapToResponse(ProjectToolAssignment assignment) {

        ProjectMasterReference project = assignment.getProject();
        ToolCatalog tool = assignment.getTool();
        CurrencyMaster currency = tool.getCurrency();

        return ProjectToolAssignmentResponseDto.builder()
                .assignmentId(assignment.getAssignmentId())
                .projectId(project.getPmsProjectId())
                .projectName(project.getProjectName())
                .toolId(tool.getToolId())
                .assetCode(tool.getAssetCode())
                .assetName(tool.getAssetName())
                .currencyId(currency.getCurrencyId())
                .currencyCode(currency.getCurrencyCode())
                .currencyName(currency.getCurrencyName())
                .quantity(assignment.getQuantity())
                .billingBasis(tool.getBillingBasis())
                .remarks(assignment.getRemarks())
                .startDate(assignment.getStartDate())
                .endDate(assignment.getEndDate())
                .isActive(assignment.getIsActive())
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getUpdatedAt())
                .build();
    }
}
