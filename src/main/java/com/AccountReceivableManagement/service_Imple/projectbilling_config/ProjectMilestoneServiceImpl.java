package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.ProjectMilestoneResponseDto;
import com.AccountReceivableManagement.entity.project_entity.ProjectMilestoneReference;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.MilestoneBillingState;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import com.AccountReceivableManagement.repo.project.ProjectMilestoneReferenceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingPaymentEntryRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.MilestoneAssignmentRow;
import com.AccountReceivableManagement.service_interface.projectbilling_config.ProjectMilestoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only view of the milestones PMS synchronises into project_milestone_reference, with each milestone's AR billing
 * state and eligibility. Writes nothing.
 */
@Service
@RequiredArgsConstructor
public class ProjectMilestoneServiceImpl implements ProjectMilestoneService {

    /** The PMS MilestoneBillingReadiness values (see ProjectMilestoneReference.billingReadiness). */
    static final Set<String> BILLING_READINESS_VALUES = Set.of("NOT_BILLABLE", "NOT_READY", "READY_FOR_BILLING");

    private final ProjectMilestoneReferenceRepository milestoneRepository;

    private final ProjectMasterReferenceRepository projectRepository;

    /** Read only: tells which milestones a billing plan already holds and how far their billing has got. */
    private final BillingPaymentEntryRepository paymentEntryRepository;

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

        List<ProjectMilestoneReference> visible = milestones.stream()
                // belt and braces: a tombstone must never reach a billing screen, whatever the query returned
                .filter(m -> !Boolean.TRUE.equals(m.getSourceDeleted()))
                .filter(m -> !billableOnly || Boolean.TRUE.equals(m.getBillable()))
                .sorted((a, b) -> Long.compare(a.getPmsMilestoneId(), b.getPmsMilestoneId()))
                .toList();

        Map<Long, List<MilestoneAssignmentRow>> assignments = visible.isEmpty()
                ? Map.of()
                : paymentEntryRepository.findMilestoneAssignmentRows(
                                visible.stream().map(ProjectMilestoneReference::getPmsMilestoneId).toList(),
                                BillingMilestonePlanServiceImpl.IN_BILLING_STATUSES)
                        .stream().collect(Collectors.groupingBy(MilestoneAssignmentRow::pmsMilestoneId));

        return visible.stream()
                .map(m -> toDto(m, assignments.getOrDefault(m.getPmsMilestoneId(), List.of())))
                .toList();
    }

    /**
     * The most advanced state among the entries that claim the milestone (normally exactly one): an invoice anywhere wins,
     * then tax processing, then a plain reservation. An occurrence flagged invoiced counts as INVOICED whatever its status.
     */
    public static MilestoneBillingState billingState(List<MilestoneAssignmentRow> rows) {
        MilestoneBillingState state = MilestoneBillingState.AVAILABLE;
        for (MilestoneAssignmentRow row : rows) {
            MilestoneBillingState rowState;
            if (Boolean.TRUE.equals(row.invoiced()) || row.occurrenceStatus() == BillingPeriodStatus.INVOICED) {
                rowState = MilestoneBillingState.INVOICED;
            } else if (row.occurrenceStatus() == BillingPeriodStatus.TAX_PENDING
                    || row.occurrenceStatus() == BillingPeriodStatus.TAX_CALCULATED) {
                rowState = MilestoneBillingState.IN_BILLING;
            } else {
                rowState = MilestoneBillingState.RESERVED;
            }
            if (rowState.ordinal() > state.ordinal()) {
                state = rowState;
            }
        }
        return state;
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

    private static ProjectMilestoneResponseDto toDto(ProjectMilestoneReference m, List<MilestoneAssignmentRow> assignments) {
        MilestoneBillingState state = billingState(assignments);
        String pmsReason = BillingMilestonePlanServiceImpl.ineligibilityReason(m);
        String reason = switch (state) {
            case AVAILABLE -> pmsReason == null ? null : "Milestone " + pmsReason + ".";
            case RESERVED -> "Milestone is already part of a billing plan.";
            case IN_BILLING -> "Milestone is already in billing.";
            case INVOICED -> "Milestone has already been invoiced.";
        };
        MilestoneAssignmentRow holder = assignments.isEmpty() ? null : assignments.get(0);

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
                .billingState(state)
                .billingConfigurationId(holder == null ? null : holder.billingConfigurationId())
                .eligibleForBilling(reason == null)
                .ineligibilityReason(reason)
                .build();
    }
}
