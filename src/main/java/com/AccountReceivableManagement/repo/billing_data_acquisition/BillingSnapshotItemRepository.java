package com.AccountReceivableManagement.repo.billing_data_acquisition;

import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshotItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Persistence for {@link BillingSnapshotItem}. Items are only ever
 * created and read through their parent {@code BillingSnapshot}.
 */
@Repository
public interface BillingSnapshotItemRepository extends JpaRepository<BillingSnapshotItem, UUID> {
}
