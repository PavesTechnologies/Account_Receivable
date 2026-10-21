package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.CurrencyMaster;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingConfigurationRepository extends JpaRepository<BillingConfiguration, UUID> {

    Optional<BillingConfiguration>
    findByProject_PmsProjectIdAndApprovalStatusAndBillingStatus(
            Long projectId,
            ApprovalStatus approvalStatus,
            BillingConfigurationStatus billingStatus
    );

    boolean existsByProject_PmsProjectIdAndApprovalStatusAndBillingStatus(
            Long projectId,
            ApprovalStatus approvalStatus,
            BillingConfigurationStatus billingStatus
    );

    List<BillingConfiguration>
    findByApprovalStatus(ApprovalStatus approvalStatus);

    List<BillingConfiguration>
    findByBillingStatus(BillingConfigurationStatus billingStatus);

    List<BillingConfiguration>
    findByApprovalStatusAndBillingStatus(
            ApprovalStatus approvalStatus,
            BillingConfigurationStatus billingStatus
    );

    List<BillingConfiguration>
    findByClientClientId(UUID clientId);


    List<BillingConfiguration>
    findByProjectPmsProjectId(Long projectId);

    /**
     * Approved, AUTOMATIC, not manually deactivated configurations whose
     * billing type is one of {@code lowerCaseBillingTypeNames} - the automatic
     * billing scheduler's Time &amp; Material configurations.
     */
    @Query("SELECT c.billingConfigurationId FROM BillingConfiguration c "
            + "WHERE c.approvalStatus = :approvalStatus "
            + "AND c.invoiceGenerationType = :invoiceGenerationType "
            + "AND c.manuallyDeactivated = false "
            + "AND LOWER(TRIM(c.billingType.billingTypeName)) IN :lowerCaseBillingTypeNames "
            + "ORDER BY c.billingConfigurationId")
    List<UUID> findAutomaticConfigurationIdsByBillingTypeNames(
            @org.springframework.data.repository.query.Param("approvalStatus") ApprovalStatus approvalStatus,
            @org.springframework.data.repository.query.Param("invoiceGenerationType")
            com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType invoiceGenerationType,
            @org.springframework.data.repository.query.Param("lowerCaseBillingTypeNames")
            java.util.Collection<String> lowerCaseBillingTypeNames);
}
