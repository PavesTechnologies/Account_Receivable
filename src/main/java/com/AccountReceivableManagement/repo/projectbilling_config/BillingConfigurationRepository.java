package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BillingConfigurationRepository extends JpaRepository<BillingConfiguration, UUID> {

    boolean existsByProject_PmsProjectIdAndStatusAndIsActive(
            Long projectId,
            BillingConfigurationStatus status,
            Boolean isActive);

    Optional<BillingConfiguration> findByProject_PmsProjectIdAndStatusAndIsActive(
            Long projectId,
            BillingConfigurationStatus status,
            Boolean isActive);
}
