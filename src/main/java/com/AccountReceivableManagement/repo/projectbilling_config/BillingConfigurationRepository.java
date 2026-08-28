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


}
