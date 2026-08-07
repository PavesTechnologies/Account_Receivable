package com.AccountReceivableManagement.repo.project_tool_billing;

import com.AccountReceivableManagement.entity.project_tool_billing.ProjectToolBillingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectToolBillingConfigRepository extends JpaRepository<ProjectToolBillingConfig, UUID> {

    Optional<ProjectToolBillingConfig> findByProjectId(Long projectId);

    boolean existsByProjectId(Long projectId);
}
