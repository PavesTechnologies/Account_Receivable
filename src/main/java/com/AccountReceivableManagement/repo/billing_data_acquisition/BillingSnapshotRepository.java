package com.AccountReceivableManagement.repo.billing_data_acquisition;

import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import org.springframework.data.jpa.repository.EntityGraph;
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

    /**
     * Fetches {@code items} eagerly so callers can read/map the snapshot's
     * line items after this method returns, without the Hibernate session
     * that loaded them still being open (avoids LazyInitializationException
     * on {@link BillingSnapshot#getItems()}).
     */
    @EntityGraph(attributePaths = "items")
    java.util.List<BillingSnapshot> findAllByProjectIdAndBillingPeriodStartAndBillingPeriodEndOrderByCreatedDateAsc(
            Long projectId, LocalDate billingPeriodStart, LocalDate billingPeriodEnd);

    /**
     * Highest acquisition batch number of the project's billing period (a
     * pre-batch snapshot with no number counts as batch 1); {@code null} when
     * the period has no snapshot yet.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT MAX(COALESCE(s.batchSequence, 1)) FROM BillingSnapshot s "
                    + "WHERE s.projectId = :projectId AND s.billingPeriodStart = :periodStart "
                    + "AND s.billingPeriodEnd = :periodEnd")
    Integer findMaxBatchSequence(
            @org.springframework.data.repository.query.Param("projectId") Long projectId,
            @org.springframework.data.repository.query.Param("periodStart") LocalDate periodStart,
            @org.springframework.data.repository.query.Param("periodEnd") LocalDate periodEnd);

    /** The batch a previous acquisition request with this idempotency key created. */
    @EntityGraph(attributePaths = "items")
    Optional<BillingSnapshot> findByBillingConfigurationIdAndIdempotencyKey(
            UUID billingConfigurationId, String idempotencyKey);

    Optional<BillingSnapshot> findBySnapshotNumber(String snapshotNumber);

    boolean existsByBillingConfigurationId(UUID billingConfigurationId);

    /**
     * Latest snapshot for a Billing Configuration - the listing's fallback when
     * no BillingAcquisition record was ever written for an existing snapshot.
     */
    Optional<BillingSnapshot> findFirstByBillingConfigurationIdOrderByCreatedDateDesc(UUID billingConfigurationId);

    /**
     * Every snapshot recorded against one Billing Configuration, latest
     * billing period first, whatever the configuration's current state - the
     * history of a deactivated or replaced configuration included. Items are
     * fetched eagerly for the same reason as the by-period lookup.
     */
    @EntityGraph(attributePaths = "items")
    java.util.List<BillingSnapshot> findAllByBillingConfigurationIdOrderByBillingPeriodStartDesc(
            UUID billingConfigurationId);

    /**
     * Backs the Invoice Generation workspace - snapshots in a given lifecycle
     * status, newest first.
     */
    java.util.List<BillingSnapshot> findAllByStatusOrderByCreatedDateDesc(
            com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus status);

    /**
     * Every billing snapshot (acquisition batch) of a billing configuration and billing period.
     * Used for Time & Material invoice generation to locate the snapshot associated
     * with a billing schedule occurrence.
     */
    java.util.List<BillingSnapshot> findAllByBillingConfigurationIdAndBillingPeriodStartAndBillingPeriodEnd(
            UUID billingConfigurationId, LocalDate billingPeriodStart, LocalDate billingPeriodEnd);

    /**
     * Whether any snapshot of the project overlaps {@code periodStart}..
     * {@code periodEnd} - i.e. the period's timesheets are already being, or
     * have been, billed (manually or through another period).
     */
    boolean existsByProjectIdAndBillingPeriodStartLessThanEqualAndBillingPeriodEndGreaterThanEqual(
            Long projectId, LocalDate periodEnd, LocalDate periodStart);

    /**
     * [billingPeriodStart, billingPeriodEnd] of every snapshot of the project
     * ending on or after {@code from} - T&amp;M discovery checks overlaps in
     * memory instead of one query per period (G-29).
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT s.billingPeriodStart, s.billingPeriodEnd FROM BillingSnapshot s "
                    + "WHERE s.projectId = :projectId AND s.billingPeriodEnd >= :from")
    java.util.List<Object[]> findPeriodsByProjectIdEndingOnOrAfter(
            @org.springframework.data.repository.query.Param("projectId") Long projectId,
            @org.springframework.data.repository.query.Param("from") LocalDate from);
}
