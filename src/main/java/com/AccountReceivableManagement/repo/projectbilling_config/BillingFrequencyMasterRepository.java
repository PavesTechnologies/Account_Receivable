package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingFrequencyMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingFrequencyMasterRepository extends JpaRepository<BillingFrequencyMaster, UUID> {
    Optional<BillingFrequencyMaster> findByBillingFrequencyNameIgnoreCase(String billingFrequencyName);

    boolean existsByBillingFrequencyNameIgnoreCase(String billingFrequencyName);

    List<BillingFrequencyMaster> findByIsActiveTrueOrderByBillingFrequencyNameAsc();
}
