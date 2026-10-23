package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.ProjectMilestoneResponseDto;
import com.AccountReceivableManagement.entity.project_entity.ProjectMilestoneReference;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import com.AccountReceivableManagement.repo.project.ProjectMilestoneReferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectMilestoneServiceImplTest {

    private static final Long PROJECT_ID = 9002L;

    @Mock
    private ProjectMilestoneReferenceRepository milestoneRepository;

    @Mock
    private ProjectMasterReferenceRepository projectRepository;

    private ProjectMilestoneServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProjectMilestoneServiceImpl(milestoneRepository, projectRepository);
    }

    private static ProjectMilestoneReference milestone(long id, String readiness, Boolean billable, boolean deleted) {
        return ProjectMilestoneReference.builder()
                .pmsMilestoneId(id)
                .pmsProjectId(PROJECT_ID)
                .milestoneCode("MS-" + id)
                .milestoneName("Milestone " + id)
                .description("desc " + id)
                .pmsEpicId(70L + id)
                .plannedStartDate(LocalDate.of(2026, 11, 1))
                .plannedDueDate(LocalDate.of(2026, 12, 15))
                .actualCompletionDate(null)
                .executionStatus("IN_PROGRESS")
                .billable(billable)
                .allocationPercentage(new BigDecimal("25.00"))
                .billingReadiness(readiness)
                .sourceVersion(3L)
                .sourceDeleted(deleted)
                .createdAt(LocalDateTime.of(2026, 10, 1, 9, 0))
                .changedAt(LocalDateTime.of(2026, 10, 2, 9, 0))
                .lastSyncedAt(LocalDateTime.of(2026, 10, 3, 9, 0))
                .build();
    }

    private void projectExists() {
        when(projectRepository.existsBypmsProjectId(PROJECT_ID)).thenReturn(true);
    }

    @Test
    void returnsEveryContractFieldWithThePmsIdAsTheIdentifier() {
        projectExists();
        when(milestoneRepository.findByPmsProjectIdAndSourceDeletedFalseOrderByPmsMilestoneIdAsc(PROJECT_ID))
                .thenReturn(List.of(milestone(5, "READY_FOR_BILLING", true, false)));

        List<ProjectMilestoneResponseDto> result = service.getMilestones(PROJECT_ID, null, false);

        assertThat(result).hasSize(1);
        ProjectMilestoneResponseDto dto = result.get(0);
        assertThat(dto.getPmsMilestoneId()).isEqualTo(5L);
        assertThat(dto.getPmsProjectId()).isEqualTo(PROJECT_ID);
        assertThat(dto.getMilestoneCode()).isEqualTo("MS-5");
        assertThat(dto.getMilestoneName()).isEqualTo("Milestone 5");
        assertThat(dto.getDescription()).isEqualTo("desc 5");
        assertThat(dto.getPmsEpicId()).isEqualTo(75L);
        assertThat(dto.getPlannedStartDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(dto.getPlannedDueDate()).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(dto.getActualCompletionDate()).isNull();
        assertThat(dto.getExecutionStatus()).isEqualTo("IN_PROGRESS");
        assertThat(dto.getBillable()).isTrue();
        assertThat(dto.getAllocationPercentage()).isEqualByComparingTo("25.00");
        assertThat(dto.getBillingReadiness()).isEqualTo("READY_FOR_BILLING");
        assertThat(dto.getSourceVersion()).isEqualTo(3L);
        assertThat(dto.getChangedAt()).isEqualTo(LocalDateTime.of(2026, 10, 2, 9, 0));
        assertThat(dto.getLastSyncedAt()).isEqualTo(LocalDateTime.of(2026, 10, 3, 9, 0));
    }

    @Test
    void usesTheQueryThatExcludesDeletedMilestonesAndNeverReturnsATombstone() {
        projectExists();
        // even if a tombstone slipped through the query it must not reach the caller
        when(milestoneRepository.findByPmsProjectIdAndSourceDeletedFalseOrderByPmsMilestoneIdAsc(PROJECT_ID))
                .thenReturn(List.of(milestone(1, "NOT_READY", true, false), milestone(2, "NOT_READY", true, true)));

        List<ProjectMilestoneResponseDto> result = service.getMilestones(PROJECT_ID, null, false);

        assertThat(result).extracting(ProjectMilestoneResponseDto::getPmsMilestoneId).containsExactly(1L);
        verify(milestoneRepository, never())
                .findByPmsProjectIdAndBillingReadinessAndSourceDeletedFalse(PROJECT_ID, "NOT_READY");
    }

    @Test
    void ordersByPmsMilestoneIdEvenIfTheRepositoryDoesNot() {
        projectExists();
        when(milestoneRepository.findByPmsProjectIdAndSourceDeletedFalseOrderByPmsMilestoneIdAsc(PROJECT_ID))
                .thenReturn(List.of(milestone(9, "NOT_READY", true, false), milestone(3, "NOT_READY", true, false),
                        milestone(6, "NOT_READY", true, false)));

        assertThat(service.getMilestones(PROJECT_ID, null, false))
                .extracting(ProjectMilestoneResponseDto::getPmsMilestoneId).containsExactly(3L, 6L, 9L);
    }

    @Test
    void filtersByBillingReadinessCaseInsensitivelyUsingTheDedicatedQuery() {
        projectExists();
        when(milestoneRepository.findByPmsProjectIdAndBillingReadinessAndSourceDeletedFalse(PROJECT_ID, "READY_FOR_BILLING"))
                .thenReturn(List.of(milestone(4, "READY_FOR_BILLING", true, false)));

        assertThat(service.getMilestones(PROJECT_ID, " ready_for_billing ", false))
                .extracting(ProjectMilestoneResponseDto::getPmsMilestoneId).containsExactly(4L);
        verify(milestoneRepository, never())
                .findByPmsProjectIdAndSourceDeletedFalseOrderByPmsMilestoneIdAsc(PROJECT_ID);
    }

    @Test
    void blankReadinessMeansNoFilter() {
        projectExists();
        when(milestoneRepository.findByPmsProjectIdAndSourceDeletedFalseOrderByPmsMilestoneIdAsc(PROJECT_ID))
                .thenReturn(List.of(milestone(1, "NOT_READY", true, false)));

        assertThat(service.getMilestones(PROJECT_ID, "  ", false)).hasSize(1);
    }

    @Test
    void billableOnlyKeepsOnlyMilestonesFlaggedBillable() {
        projectExists();
        when(milestoneRepository.findByPmsProjectIdAndSourceDeletedFalseOrderByPmsMilestoneIdAsc(PROJECT_ID))
                .thenReturn(List.of(milestone(1, "NOT_BILLABLE", false, false), milestone(2, "READY_FOR_BILLING", true, false),
                        milestone(3, "NOT_READY", null, false)));

        assertThat(service.getMilestones(PROJECT_ID, null, true))
                .extracting(ProjectMilestoneResponseDto::getPmsMilestoneId).containsExactly(2L);
        assertThat(service.getMilestones(PROJECT_ID, null, false)).hasSize(3);
    }

    @Test
    void aKnownProjectWithNoMilestonesYieldsAnEmptyListNotAnError() {
        projectExists();
        when(milestoneRepository.findByPmsProjectIdAndSourceDeletedFalseOrderByPmsMilestoneIdAsc(PROJECT_ID))
                .thenReturn(List.of());

        assertThat(service.getMilestones(PROJECT_ID, null, false)).isEmpty();
    }

    @Test
    void anUnknownProjectIsNotFoundAndNeverQueriesMilestones() {
        when(projectRepository.existsBypmsProjectId(404L)).thenReturn(false);

        assertThatThrownBy(() -> service.getMilestones(404L, null, false))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessageContaining("404");
        verifyNoInteractions(milestoneRepository);
    }

    @Test
    void anUnknownReadinessValueIsRejectedBeforeAnyLookup() {
        assertThatThrownBy(() -> service.getMilestones(PROJECT_ID, "ELIGIBLE", false))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("NOT_BILLABLE")
                .hasMessageContaining("READY_FOR_BILLING");
        verifyNoInteractions(milestoneRepository, projectRepository);
    }

    @Test
    void aMissingProjectIdIsRejected() {
        assertThatThrownBy(() -> service.getMilestones(null, null, false))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class);
        verifyNoInteractions(milestoneRepository, projectRepository);
    }

    @Test
    void theServiceOnlyReadsAndNeverWrites() {
        projectExists();
        when(milestoneRepository.findByPmsProjectIdAndSourceDeletedFalseOrderByPmsMilestoneIdAsc(PROJECT_ID))
                .thenReturn(List.of(milestone(1, "NOT_READY", true, false)));

        service.getMilestones(PROJECT_ID, null, false);

        verify(milestoneRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(milestoneRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
        verify(milestoneRepository, never()).delete(org.mockito.ArgumentMatchers.any());
        verify(milestoneRepository, never()).deleteById(org.mockito.ArgumentMatchers.any());
    }
}
