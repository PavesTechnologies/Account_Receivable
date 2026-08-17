package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.CurrencyMaster;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    /**
     * Returns all billing configurations where is_active = true (1).
     *
     * An explicit JPQL query is used instead of the derived name
     * findByIsActiveTrue() because Spring Data's parser can misread
     * the leading 'is' in the field name isActive, causing it to
     * match rows it should not (as seen: is_active=0 rows appeared).
     */
    @Query("SELECT bc FROM BillingConfiguration bc WHERE bc.isActive = true")
    List<BillingConfiguration> findAllActive();

    List<BillingConfiguration> findByClientClientId(UUID clientId);

    List<BillingConfiguration> findByProjectPmsProjectId(Long projectId);

//    Optional<CurrencyMaster> findByCurrencyCodeIgnoreCase(String currencyCode);
}
