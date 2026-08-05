package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingConfigurationRepository extends JpaRepository<BillingConfiguration, UUID> {

    boolean existsByProject_PmsProjectIdAndStatusAndIsActive(
            Long projectId,
            BillingConfigurationStatus status,
            Boolean isActive);

    Optional<BillingConfiguration> findByProject_PmsProjectIdAndStatusAndIsActive(
            Long projectId,
            BillingConfigurationStatus status,
            Boolean isActive);

    boolean existsByProjectAndStatusAndIsActiveTrue(
            ProjectMasterReference project,
            BillingConfigurationStatus status);

    List<BillingConfiguration> findByStatus(BillingConfigurationStatus status);

    List<BillingConfiguration> findByClientClientId(UUID clientId);

    List<BillingConfiguration> findByProjectPmsProjectId(Long projectId);
}
