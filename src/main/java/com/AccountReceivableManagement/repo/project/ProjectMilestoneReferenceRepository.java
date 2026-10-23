package com.AccountReceivableManagement.repo.project;

import com.AccountReceivableManagement.entity.project_entity.ProjectMilestoneReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectMilestoneReferenceRepository extends JpaRepository<ProjectMilestoneReference, Long> {

    List<ProjectMilestoneReference> findByPmsProjectIdAndSourceDeletedFalseOrderByPmsMilestoneIdAsc(Long pmsProjectId);

    List<ProjectMilestoneReference> findByPmsProjectIdAndBillingReadinessAndSourceDeletedFalse(
            Long pmsProjectId, String billingReadiness);
}
