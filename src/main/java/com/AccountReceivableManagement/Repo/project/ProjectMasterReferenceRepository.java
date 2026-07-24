package com.AccountReceivableManagement.Repo.project;

import com.AccountReceivableManagement.Entity.project_entity.ProjectMasterReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectMasterReferenceRepository extends JpaRepository<ProjectMasterReference, Long> {

    boolean existsBypmsProjectId(Long pmsProjectId);

    Optional<ProjectMasterReference> findBypmsProjectId(Long pmsProjectId);
}
