package com.AccountReceivableManagement.repo.project_tool_assignment;

import com.AccountReceivableManagement.entity.project_tool_assignment.ProjectToolAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectToolAssignmentRepository extends JpaRepository<ProjectToolAssignment, UUID> {

    @Query("SELECT a FROM ProjectToolAssignment a WHERE a.project.pmsProjectId = :projectId")
    List<ProjectToolAssignment> findByProjectId(@Param("projectId") Long projectId);

    @Query("SELECT a FROM ProjectToolAssignment a WHERE a.tool.toolId = :toolId")
    List<ProjectToolAssignment> findByToolId(@Param("toolId") UUID toolId);

    @Query("SELECT a FROM ProjectToolAssignment a WHERE a.isActive = true")
    List<ProjectToolAssignment> findActiveAssignments();

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM ProjectToolAssignment a "
            + "WHERE a.project.pmsProjectId = :projectId "
            + "AND a.tool.toolId = :toolId "
            + "AND a.isActive = true "
            + "AND (:excludeAssignmentId IS NULL OR a.assignmentId <> :excludeAssignmentId) "
            + "AND (a.startDate <= :endDate OR :endDate IS NULL) "
            + "AND (:startDate <= a.endDate OR a.endDate IS NULL)")
    boolean existsOverlappingAssignment(
            @Param("projectId") Long projectId,
            @Param("toolId") UUID toolId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeAssignmentId") UUID excludeAssignmentId);

    /**
     * Active assignments for a project whose effective period overlaps the given billing period
     * (open-ended end dates are treated as continuing indefinitely). Used by Billing Data Acquisition.
     */
    @Query("SELECT a FROM ProjectToolAssignment a "
            + "WHERE a.project.pmsProjectId = :projectId "
            + "AND a.isActive = true "
            + "AND (a.startDate <= :periodEnd OR :periodEnd IS NULL) "
            + "AND (:periodStart <= a.endDate OR a.endDate IS NULL)")
    List<ProjectToolAssignment> findActiveAssignmentsForBillingPeriod(
            @Param("projectId") Long projectId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);
}
