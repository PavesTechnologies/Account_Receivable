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

    void deleteByBillingConfiguration(
            BillingConfiguration billingConfiguration);

    /** Renewal terms of the configuration in the given approval state. */
    List<BillingRecurringConfiguration>
    findByBillingConfigurationAndRenewalApprovalStatus(
            BillingConfiguration billingConfiguration,
            com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus renewalApprovalStatus);

    List<BillingRecurringConfiguration>
    findByRenewedFrom(
            BillingRecurringConfiguration renewedFrom);
}
