package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingTypeMasterRepository extends JpaRepository<BillingTypeMaster, UUID> {
    Optional<BillingTypeMaster> findByBillingTypeNameIgnoreCase(String billingTypeName);

    boolean existsByBillingTypeNameIgnoreCase(String billingTypeName);

    List<BillingTypeMaster> findByIsActiveTrueOrderByBillingTypeNameAsc();

    BillingTypeMaster save(BillingTypeMaster billingType);
}
