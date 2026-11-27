package com.AccountReceivableManagement.repo.billing_data_acquisition;

import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshotItem;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Persistence for {@link BillingSnapshotItem}. Items are only ever
 * created and read through their parent {@code BillingSnapshot}.
 */
@Repository
public interface BillingSnapshotItemRepository extends JpaRepository<BillingSnapshotItem, UUID> {

    boolean existsBySourceReferenceId(String sourceReferenceId);

    /**
     * The expense lines of this lineItemId already sitting on a live (non-cancelled) snapshot other than
     * {@code excludedSnapshotId} - the snapshot being rebuilt, or {@code null} when a new batch is
     * acquired. A billing period can hold several batches, so the exclusion is by snapshot, never by
     * project and period. Returned (not just counted) so the caller can tell a repeat of the same handoff
     * from a correction, and whether the billed snapshot is already invoiced.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT i FROM BillingSnapshotItem i
            WHERE i.itemType = com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType.EXPENSE
              AND i.sourceReferenceId = :lineItemId
              AND i.billingSnapshot.status <> com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus.CANCELLED
              AND (:excludedSnapshotId IS NULL OR i.billingSnapshot.id <> :excludedSnapshotId)
            """)
    java.util.List<BillingSnapshotItem> findExpenseItemsInOtherSnapshots(
            @org.springframework.data.repository.query.Param("lineItemId") String lineItemId,
            @org.springframework.data.repository.query.Param("excludedSnapshotId") UUID excludedSnapshotId);

    boolean existsBySourceReferenceIdAndBillingSnapshot_BillingPeriodStartAndBillingSnapshot_BillingPeriodEnd(
            String sourceReferenceId,
            LocalDate billingPeriodStart,
            LocalDate billingPeriodEnd);

    /**
     * Source-level idempotency: which of {@code sourceReferenceIds} are
     * already carried, as {@code itemType}, by a Billing Snapshot other than
     * {@code excludedSnapshotId} (pass {@code null} when creating a new
     * snapshot) that is not in {@code ignoredStatus}. Backs the
     * already-billed timesheet check - an item belongs to its snapshot from
     * acquisition onward, so a not-yet-invoiced snapshot still claims it.
     */
    @Query("SELECT DISTINCT i.billedSourceReferenceId FROM BillingSnapshotItem i "
            + "WHERE i.itemType = :itemType "
            + "AND i.billedSourceReferenceId IN :sourceReferenceIds "
            + "AND i.billedSourceReferenceId IS NOT NULL "
            + "AND i.billingSnapshot.status <> :ignoredStatus "
            + "AND (:excludedSnapshotId IS NULL OR i.billingSnapshot.id <> :excludedSnapshotId)")
    List<String> findClaimedSourceReferenceIds(
            @Param("itemType") BillingItemType itemType,
            @Param("sourceReferenceIds") Collection<String> sourceReferenceIds,
            @Param("ignoredStatus") BillingSnapshotStatus ignoredStatus,
            @Param("excludedSnapshotId") UUID excludedSnapshotId);

    /**
     * Source-level idempotency with billing configuration status check:
     * returns claimed timesheet source reference IDs only if their snapshot's
     * billing configuration is ACTIVE. INACTIVE billing configurations can be
     * re-used. Used to determine if already-billed timesheets should block
     * a new acquisition attempt.
     */
    @Query("SELECT DISTINCT i.billedSourceReferenceId FROM BillingSnapshotItem i "
            + "JOIN BillingConfiguration bc ON i.billingSnapshot.billingConfigurationId = bc.billingConfigurationId "
            + "WHERE i.itemType = :itemType "
            + "AND i.billedSourceReferenceId IN :sourceReferenceIds "
            + "AND i.billedSourceReferenceId IS NOT NULL "
            + "AND i.billingSnapshot.status <> :ignoredStatus "
            + "AND (:excludedSnapshotId IS NULL OR i.billingSnapshot.id <> :excludedSnapshotId) "
            + "AND bc.billingStatus = "
            + "com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus.ACTIVE")
    List<String> findClaimedSourceReferenceIdsByActiveConfiguration(
            @Param("itemType") BillingItemType itemType,
            @Param("sourceReferenceIds") Collection<String> sourceReferenceIds,
            @Param("ignoredStatus") BillingSnapshotStatus ignoredStatus,
            @Param("excludedSnapshotId") UUID excludedSnapshotId);
}
