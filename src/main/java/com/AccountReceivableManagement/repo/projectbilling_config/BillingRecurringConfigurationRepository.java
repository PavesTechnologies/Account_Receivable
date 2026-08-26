package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingRecurringConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingRecurringConfigurationRepository extends JpaRepository<BillingRecurringConfiguration, UUID> {

    Optional<BillingRecurringConfiguration>
    findByBillingConfigurationAndIsActiveTrue(
            BillingConfiguration billingConfiguration);

    List<BillingRecurringConfiguration>
    findAllByBillingConfigurationAndIsActiveTrue(
            BillingConfiguration billingConfiguration);

    boolean existsByBillingConfigurationAndIsActiveTrue(
            BillingConfiguration billingConfiguration);
}
