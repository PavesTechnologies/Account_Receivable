package com.AccountReceivableManagement.repo.tax_calculation;

import com.AccountReceivableManagement.entity.tax_calculation.TaxCalculation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxCalculationRepository extends JpaRepository<TaxCalculation, UUID> {

    Optional<TaxCalculation>
    findByBillingSnapshotId(UUID billingSnapshotId);

    boolean existsByBillingSnapshotId(UUID billingSnapshotId);
}
