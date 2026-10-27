package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingMilestonePlan;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingPaymentEntry;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface BillingPaymentEntryRepository extends JpaRepository<BillingPaymentEntry, UUID> {

    // ---------------------------------------------------------------------------------------------------------------
    // The ONE JPQL definition of "this entry's occurrence has started billing", shared by findEntriesInBilling,
    // findBilledAssignments and findMilestoneAssignmentRows (aliases: e = entry, c = its configuration, s = occurrence).
    // Its Java counterpart is BillingMilestonePlanServiceImpl.billingStarted / singleDayOccurrences / isFrozen, used by
    // plan update, delete and revalidation on locked rows and by the plan response. JPQL cannot call Java, so the two
    // are kept in step by the parity tests in MilestoneAssignmentQueriesTest; the status list itself is a single Java
    // constant (BillingMilestonePlanServiceImpl.IN_BILLING_STATUSES) bound to :inBillingStatuses by every caller.
    // Constants only (compile-time strings), so the queries below are textually identical to what they were before.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The occurrence that belongs to an entry: same configuration as the entry's plan, a single day equal to the entry's
     * billing date, and active. (No foreign key links an entry to its occurrence; occurrence generation creates exactly one
     * single-day occurrence per entry date, unique per configuration.)
     */
    String OCCURRENCE_OF_ENTRY = "s.billingConfiguration = c and s.periodStartDate = e.billingDate "
            + "and s.periodEndDate = e.billingDate and s.isActive = true";

    /** Billing has started for the occurrence {@code s}: it is flagged invoiced or is in one of {@code :inBillingStatuses}. */
    String BILLING_STARTED = "(s.isInvoiced = true or s.periodStatus in :inBillingStatuses)";

    List<BillingPaymentEntry> findByMilestonePlanAndIsActiveTrueOrderBySequenceAsc(BillingMilestonePlan milestonePlan);

    List<BillingPaymentEntry> findByMilestonePlanAndIsActiveTrue(BillingMilestonePlan milestonePlan);

    List<BillingPaymentEntry> findByMilestonePlan(BillingMilestonePlan milestonePlan);

    void deleteByMilestonePlan(BillingMilestonePlan milestonePlan);

    /**
     * Active payment entries of OTHER billing configurations that already claim any of the given PMS milestones. "Other
     * live configuration" means the entry and its plan are active and the owning configuration is not EXPIRED (DRAFT,
     * PENDING_APPROVAL, APPROVED and REJECTED configurations all hold their milestones). A REJECTED configuration can
     * return to DRAFT on edit ("allow correction and resubmission"), so releasing its milestones would let it come back
     * with a claim somebody else has meanwhile taken; it releases them only by being edited to other milestones, deleted
     * or expired. (Business rule to be confirmed - see the Phase 2.1 report.)
     *
     * A locking read (SELECT ... FOR SHARE) on purpose: MySQL's default REPEATABLE READ would otherwise answer from the
     * transaction's snapshot and miss a plan another transaction committed a moment ago.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select e from BillingPaymentEntry e
              join e.milestonePlan p
              join p.billingConfiguration c
             where e.pmsMilestoneId in :milestoneIds
               and e.isActive = true
               and p.isActive = true
               and c.billingConfigurationId <> :billingConfigurationId
               and c.billingStatus <> com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus.EXPIRED
             order by e.pmsMilestoneId asc
            """)
    List<BillingPaymentEntry> findActiveAssignmentsInOtherConfigurations(
            @Param("milestoneIds") Collection<Long> milestoneIds,
            @Param("billingConfigurationId") UUID billingConfigurationId);

    /**
     * Active entries of ONE plan whose billing occurrence has left the plan's control: the occurrence is active and either
     * invoiced or in TAX_PENDING / TAX_CALCULATED / INVOICED ({@code inBillingStatuses}). There is no foreign key from an
     * entry to its occurrence: occurrence generation creates exactly one single-day occurrence per entry on the entry's
     * billing date (unique per configuration and date), so (configuration, billing date) is the link. These entries are
     * frozen - they can be neither changed nor removed - and their milestone is billed for good.
     *
     * No production code calls this any more: plan update, delete and revalidation decide from the LOCKED occurrence rows
     * in Java (a plain read can use an older repeatable-read snapshot). It is kept as the plan-scoped statement of the same
     * definition and is exercised by the parity tests.
     */
    @Query("select e from BillingPaymentEntry e join e.milestonePlan p join p.billingConfiguration c "
            + "where p = :plan and e.isActive = true "
            + "and exists (select 1 from BillingSchedule s where " + OCCURRENCE_OF_ENTRY + " and " + BILLING_STARTED + ") "
            + "order by e.sequence asc")
    List<BillingPaymentEntry> findEntriesInBilling(
            @Param("plan") BillingMilestonePlan plan,
            @Param("inBillingStatuses") Collection<BillingPeriodStatus> inBillingStatuses);

    /**
     * Active entries of ANY billing configuration - whatever its approval or billing status, EXPIRED included - that bill one
     * of the given PMS milestones and whose occurrence is already in billing (see {@link #findEntriesInBilling}). Unlike a
     * plain reservation, a milestone that has reached billing is never handed out again, not even when its configuration
     * has since expired. Locking read for the same reason as {@link #findActiveAssignmentsInOtherConfigurations}.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select e from BillingPaymentEntry e join e.milestonePlan p join p.billingConfiguration c "
            + "where e.pmsMilestoneId in :milestoneIds and e.isActive = true and p.isActive = true "
            + "and exists (select 1 from BillingSchedule s where " + OCCURRENCE_OF_ENTRY + " and " + BILLING_STARTED + ") "
            + "order by e.pmsMilestoneId asc")
    List<BillingPaymentEntry> findBilledAssignments(
            @Param("milestoneIds") Collection<Long> milestoneIds,
            @Param("inBillingStatuses") Collection<BillingPeriodStatus> inBillingStatuses);

    /**
     * The active milestone entries of a configuration's active plan that bill on the given date - the entry behind a
     * single-day milestone occurrence. Used right before the occurrence starts billing.
     */
    @Query("""
            select e from BillingPaymentEntry e
              join e.milestonePlan p
             where p.billingConfiguration = :configuration
               and p.isActive = true
               and e.isActive = true
               and e.billingDate = :billingDate
               and e.pmsMilestoneId is not null
            """)
    List<BillingPaymentEntry> findActiveMilestoneEntries(
            @Param("configuration") com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration configuration,
            @Param("billingDate") java.time.LocalDate billingDate);

    /**
     * Where each of the given milestones stands in billing, one row per active entry that claims it (normally one). The
     * occurrence columns are null while no occurrence exists yet (plan not approved, or still to be generated). Read-only;
     * used to show a milestone's billing state, never to decide anything.
     *
     * Same business rule as the claim queries: an EXPIRED configuration does not reserve a milestone
     * ({@link #findActiveAssignmentsInOtherConfigurations} excludes it), so its rows are listed only when billing has
     * started - the occurrence is flagged invoiced or is in {@code inBillingStatuses} - exactly the condition of
     * {@link #findBilledAssignments}. A milestone an expired configuration has billed therefore stays unavailable, and one it
     * merely reserved is available again.
     */
    @Query("select new com.AccountReceivableManagement.repo.projectbilling_config.MilestoneAssignmentRow("
            + "e.pmsMilestoneId, c.billingConfigurationId, c.approvalStatus, c.billingStatus, "
            + "s.billingScheduleId, s.periodStatus, s.isInvoiced) "
            + "from BillingPaymentEntry e join e.milestonePlan p join p.billingConfiguration c "
            + "left join BillingSchedule s on " + OCCURRENCE_OF_ENTRY + " "
            + "where e.pmsMilestoneId in :milestoneIds and e.isActive = true and p.isActive = true "
            + "and (c.billingStatus <> com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus.EXPIRED "
            + "or " + BILLING_STARTED + ") "
            + "order by e.pmsMilestoneId asc")
    List<MilestoneAssignmentRow> findMilestoneAssignmentRows(
            @Param("milestoneIds") Collection<Long> milestoneIds,
            @Param("inBillingStatuses") Collection<BillingPeriodStatus> inBillingStatuses);

}
