package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.ProjectMilestoneResponseDto;
import com.AccountReceivableManagement.entity.project_entity.ProjectMilestoneReference;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import com.AccountReceivableManagement.repo.project.ProjectMilestoneReferenceRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.ProjectMilestoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Read-only view of the milestones PMS synchronises into project_milestone_reference. Writes nothing. */
@Service
@RequiredArgsConstructor
public class ProjectMilestoneServiceImpl implements ProjectMilestoneService {

    /** The PMS MilestoneBillingReadiness values (see ProjectMilestoneReference.billingReadiness). */
    static final Set<String> BILLING_READINESS_VALUES = Set.of("NOT_BILLABLE", "NOT_READY", "READY_FOR_BILLING");

    private final ProjectMilestoneReferenceRepository milestoneRepository;

    private final ProjectMasterReferenceRepository projectRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProjectMilestoneResponseDto> getMilestones(Long pmsProjectId, String billingReadiness, boolean billableOnly) {

        if (pmsProjectId == null) {
            throw new GlobalExceptionHandler.ValidationException("Project id is required.");
        }

        String readiness = normaliseReadiness(billingReadiness);

        if (!projectRepository.existsBypmsProjectId(pmsProjectId)) {
            throw new GlobalExceptionHandler.ResourceNotFoundException("Project " + pmsProjectId + " was not found.");
        }

        List<ProjectMilestoneReference> milestones = readiness == null
                ? milestoneRepository.findByPmsProjectIdAndSourceDeletedFalseOrderByPmsMilestoneIdAsc(pmsProjectId)
                : milestoneRepository.findByPmsProjectIdAndBillingReadinessAndSourceDeletedFalse(pmsProjectId, readiness);

        return milestones.stream()
                // belt and braces: a tombstone must never reach a billing screen, whatever the query returned
                .filter(m -> !Boolean.TRUE.equals(m.getSourceDeleted()))
                .filter(m -> !billableOnly || Boolean.TRUE.equals(m.getBillable()))
                .sorted((a, b) -> Long.compare(a.getPmsMilestoneId(), b.getPmsMilestoneId()))
                .map(ProjectMilestoneServiceImpl::toDto)
                .toList();
    }

    private static String normaliseReadiness(String billingReadiness) {
        if (billingReadiness == null || billingReadiness.isBlank()) {
            return null;
        }
        String value = billingReadiness.trim().toUpperCase(Locale.ROOT);
        if (!BILLING_READINESS_VALUES.contains(value)) {
            throw new GlobalExceptionHandler.ValidationException(
                    "billingReadiness must be one of NOT_BILLABLE, NOT_READY, READY_FOR_BILLING.");
        }
        return value;
    }

    private static ProjectMilestoneResponseDto toDto(ProjectMilestoneReference m) {
        return ProjectMilestoneResponseDto.builder()
                .pmsMilestoneId(m.getPmsMilestoneId())
                .pmsProjectId(m.getPmsProjectId())
                .milestoneCode(m.getMilestoneCode())
                .milestoneName(m.getMilestoneName())
                .description(m.getDescription())
                .pmsEpicId(m.getPmsEpicId())
                .plannedStartDate(m.getPlannedStartDate())
                .plannedDueDate(m.getPlannedDueDate())
                .actualCompletionDate(m.getActualCompletionDate())
                .executionStatus(m.getExecutionStatus())
                .billable(m.getBillable())
                .allocationPercentage(m.getAllocationPercentage())
                .billingReadiness(m.getBillingReadiness())
                .sourceVersion(m.getSourceVersion())
                .createdAt(m.getCreatedAt())
                .changedAt(m.getChangedAt())
                .lastSyncedAt(m.getLastSyncedAt())
                .build();
    }
}
