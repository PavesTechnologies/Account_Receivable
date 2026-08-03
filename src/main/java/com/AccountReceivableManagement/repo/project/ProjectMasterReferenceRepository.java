package com.AccountReceivableManagement.repo.project;

import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectMasterReferenceRepository extends JpaRepository<ProjectMasterReference, Long> {

    boolean existsBypmsProjectId(Long pmsProjectId);

    Optional<ProjectMasterReference> findBypmsProjectId(Long pmsProjectId);

    List<ProjectMasterReference> findByClientIdOrderByProjectNameAsc(UUID clientId);
}
