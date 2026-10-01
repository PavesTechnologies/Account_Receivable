package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingMilestonePlan;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingPaymentEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BillingPaymentEntryRepository extends JpaRepository<BillingPaymentEntry, UUID> {

    List<BillingPaymentEntry> findByMilestonePlanAndIsActiveTrueOrderBySequenceAsc(BillingMilestonePlan milestonePlan);

    List<BillingPaymentEntry> findByMilestonePlanAndIsActiveTrue(BillingMilestonePlan milestonePlan);

    void deleteByMilestonePlan(BillingMilestonePlan milestonePlan);

}
