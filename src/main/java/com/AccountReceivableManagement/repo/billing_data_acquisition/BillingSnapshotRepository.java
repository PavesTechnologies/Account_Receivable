package com.AccountReceivableManagement.repo.billing_data_acquisition;

import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for the {@link BillingSnapshot} aggregate root.
 */
@Repository
public interface BillingSnapshotRepository extends JpaRepository<BillingSnapshot, UUID> {

    boolean existsByProjectIdAndBillingPeriodStartAndBillingPeriodEnd(
            Long projectId, LocalDate billingPeriodStart, LocalDate billingPeriodEnd);

    Optional<BillingSnapshot> findByProjectIdAndBillingPeriodStartAndBillingPeriodEnd(
            Long projectId, LocalDate billingPeriodStart, LocalDate billingPeriodEnd);

    Optional<BillingSnapshot> findBySnapshotNumber(String snapshotNumber);
}
