package com.AccountReceivableManagement.repo.software_billing_history;

import com.AccountReceivableManagement.entity.software_billing_history.SoftwareBillingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface SoftwareBillingHistoryRepository extends JpaRepository<SoftwareBillingHistory, UUID> {

    boolean existsByAssetIdAndBillingPeriodStartAndBillingPeriodEnd(
            UUID assetId, LocalDate billingPeriodStart, LocalDate billingPeriodEnd);

    List<SoftwareBillingHistory> findByAssetId(UUID assetId);

    List<SoftwareBillingHistory> findByBillingSnapshotId(UUID billingSnapshotId);
}
