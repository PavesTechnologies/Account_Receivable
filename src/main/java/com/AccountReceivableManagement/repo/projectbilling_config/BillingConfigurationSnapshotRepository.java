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

    /** The newest snapshot not yet restored or closed: the one guarding the edit that is pending right now. */
    Optional<BillingConfigurationSnapshot> findFirstByBillingConfigurationAndIsRestoredFalseOrderByCreatedAtDesc(
            BillingConfiguration billingConfiguration);

    /** Every snapshot still open for the configuration (approval closes them all). */
    List<BillingConfigurationSnapshot> findByBillingConfigurationAndIsRestoredFalseOrderByCreatedAtDesc(
            BillingConfiguration billingConfiguration);

    /** Change-tracking history of a configuration (foreign key to it); blocks deleting the configuration. */
    boolean existsByBillingConfiguration_BillingConfigurationId(UUID billingConfigurationId);
}
