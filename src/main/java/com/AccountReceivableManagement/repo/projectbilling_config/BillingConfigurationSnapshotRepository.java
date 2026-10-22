package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfigurationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingConfigurationSnapshotRepository extends JpaRepository<BillingConfigurationSnapshot, UUID> {

    List<BillingConfigurationSnapshot> findByBillingConfigurationOrderByCreatedAtDesc(
            BillingConfiguration billingConfiguration);

    Optional<BillingConfigurationSnapshot> findFirstByBillingConfigurationOrderByCreatedAtDesc(
            BillingConfiguration billingConfiguration);

    Optional<BillingConfigurationSnapshot> findByBillingConfigurationAndIsRestoredFalse(
            BillingConfiguration billingConfiguration);

    /** Change-tracking history of a configuration (foreign key to it); blocks deleting the configuration. */
    boolean existsByBillingConfiguration_BillingConfigurationId(UUID billingConfigurationId);
}
