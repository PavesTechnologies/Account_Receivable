package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingMilestonePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingMilestonePlanRepository extends JpaRepository<BillingMilestonePlan, UUID> {

    Optional<BillingMilestonePlan> findByBillingConfigurationAndIsActiveTrue(BillingConfiguration billingConfiguration);

    boolean existsByBillingConfigurationAndIsActiveTrue(BillingConfiguration billingConfiguration);

    void deleteByBillingConfiguration(BillingConfiguration billingConfiguration);

}
