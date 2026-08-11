package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTMRateCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BillingTMRateCardRepository extends JpaRepository<BillingTMRateCard, UUID> {
    List<BillingTMRateCard> findByBillingConfigurationAndIsActiveTrueOrderByRoleNameAsc(
            BillingConfiguration billingConfiguration);

    boolean existsByBillingConfigurationAndRoleNameIgnoreCaseAndIsActiveTrue(
            BillingConfiguration billingConfiguration,
            String roleName);

    boolean existsByBillingConfiguration(
            BillingConfiguration billingConfiguration);

    boolean existsByBillingConfigurationAndIsActiveTrue(
            BillingConfiguration billingConfiguration);

    long countByBillingConfigurationAndIsActiveTrue(
            BillingConfiguration billingConfiguration);

}
