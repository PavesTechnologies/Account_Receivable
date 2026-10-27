package com.AccountReceivableManagement.repo.project;

import com.AccountReceivableManagement.entity.project_entity.ProjectMilestoneReference;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProjectMilestoneReferenceRepository extends JpaRepository<ProjectMilestoneReference, Long> {

    /**
     * The given milestones (tombstones included - the caller decides what a deleted one means), each row locked
     * (SELECT ... FOR UPDATE) until the surrounding transaction ends, in ascending id order so two transactions claiming
     * overlapping milestones always lock in the same order and cannot deadlock. A billing plan takes this lock before it
     * checks whether another plan already claims the milestone, which serialises concurrent claims on the same milestone
     * (MySQL has no partial unique index to enforce "one active plan per milestone" in the database).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ProjectMilestoneReference m where m.pmsMilestoneId in :ids order by m.pmsMilestoneId asc")
    List<ProjectMilestoneReference> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    List<ProjectMilestoneReference> findByPmsProjectIdAndSourceDeletedFalseOrderByPmsMilestoneIdAsc(Long pmsProjectId);

    List<ProjectMilestoneReference> findByPmsProjectIdAndBillingReadinessAndSourceDeletedFalse(
            Long pmsProjectId, String billingReadiness);
}
