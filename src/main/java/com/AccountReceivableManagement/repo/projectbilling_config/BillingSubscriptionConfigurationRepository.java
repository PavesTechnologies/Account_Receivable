package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSubscriptionConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingSubscriptionConfigurationRepository extends JpaRepository<BillingSubscriptionConfiguration, UUID> {

    Optional<BillingSubscriptionConfiguration>
    findByBillingConfigurationAndIsActiveTrue(
            BillingConfiguration billingConfiguration);

    boolean existsByBillingConfigurationAndIsActiveTrue(
            BillingConfiguration billingConfiguration);
}
