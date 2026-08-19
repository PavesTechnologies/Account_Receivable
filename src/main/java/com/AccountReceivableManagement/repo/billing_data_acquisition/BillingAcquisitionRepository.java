package com.AccountReceivableManagement.repo.billing_data_acquisition;

import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingAcquisition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingAcquisitionRepository extends JpaRepository<BillingAcquisition, UUID> {

    Optional<BillingAcquisition> findByBillingConfiguration_BillingConfigurationIdAndBillingPeriodStartAndBillingPeriodEnd(
            UUID billingConfigurationId,
            LocalDate billingPeriodStart,
            LocalDate billingPeriodEnd
    );

    Optional<BillingAcquisition> findFirstByBillingConfiguration_BillingConfigurationIdAndBillingPeriodStartAndBillingPeriodEnd(
            UUID billingConfigurationId,
            LocalDate billingPeriodStart,
            LocalDate billingPeriodEnd
    );

    Optional<BillingAcquisition> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);

    Optional<BillingAcquisition> findFirstByBillingConfiguration_BillingConfigurationIdOrderByCreatedAtDesc(UUID billingConfigurationId);

    List<BillingAcquisition> findByProjectId(Long projectId);
}
