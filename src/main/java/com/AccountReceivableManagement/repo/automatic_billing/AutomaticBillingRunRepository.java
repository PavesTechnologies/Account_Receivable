package com.AccountReceivableManagement.repo.automatic_billing;

import com.AccountReceivableManagement.entity.automatic_billing.AutomaticBillingRun;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AutomaticBillingRunRepository extends JpaRepository<AutomaticBillingRun, UUID> {

    /** Runs that created or reused the invoice (idx_automatic_billing_run_invoice). */
    java.util.List<AutomaticBillingRun> findAllByInvoiceId(UUID invoiceId);

    /** The run of an occurrence-based billing event (at most one - uk_automatic_billing_run_schedule). */
    Optional<AutomaticBillingRun> findByBillingScheduleId(UUID billingScheduleId);

    /** The run of a billing event by its business key (at most one - uk_automatic_billing_run_period). */
    Optional<AutomaticBillingRun> findByBillingConfiguration_BillingConfigurationIdAndBillingPeriodStartAndBillingPeriodEnd(
            UUID billingConfigurationId,
            LocalDate billingPeriodStart,
            LocalDate billingPeriodEnd);

    // ---------------------------------------------------------------- monitoring counts

    long countByStatus(AutomaticBillingRunStatus status);

    /** FAILED runs with no automatic retry left - they need manual intervention. */
    long countByStatusAndNextRetryAtIsNull(AutomaticBillingRunStatus status);

    /** FAILED runs waiting for their scheduled retry. */
    long countByStatusAndNextRetryAtIsNotNull(AutomaticBillingRunStatus status);

    /** PROCESSING runs whose worker stopped renewing its claim (crash, restart, stall). */
    long countByStatusAndClaimExpiresAtBefore(AutomaticBillingRunStatus status, LocalDateTime now);

    /** Guards deletion of a configuration that automatic billing has history for. */
    boolean existsByBillingConfiguration_BillingConfigurationId(UUID billingConfigurationId);

    /** Ids of runs in {@code status}, oldest first, bounded by {@code page}. */
    @Query("SELECT r.automaticBillingRunId FROM AutomaticBillingRun r WHERE r.status = :status "
            + "ORDER BY r.createdAt ASC, r.automaticBillingRunId ASC")
    java.util.List<UUID> findRunIdsByStatus(@Param("status") AutomaticBillingRunStatus status,
            org.springframework.data.domain.Pageable page);

    /**
     * FAILED runs whose scheduled retry is due, earliest first. A run
     * without {@code nextRetryAt} (permanent failure, attempts exhausted) is
     * never returned: it waits for manual intervention.
     */
    @Query("SELECT r.automaticBillingRunId FROM AutomaticBillingRun r "
            + "WHERE r.status = com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus.FAILED "
            + "AND r.nextRetryAt IS NOT NULL AND r.nextRetryAt <= :now "
            + "ORDER BY r.nextRetryAt ASC, r.automaticBillingRunId ASC")
    java.util.List<UUID> findRunIdsDueForRetry(@Param("now") LocalDateTime now,
            org.springframework.data.domain.Pageable page);

    /**
     * PROCESSING runs whose worker's claim has expired - the worker stopped
     * (crash, restart) or stalled; an active worker renews its claim before
     * every step. Oldest expiry first.
     */
    @Query("SELECT r.automaticBillingRunId FROM AutomaticBillingRun r "
            + "WHERE r.status = com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus.PROCESSING "
            + "AND r.claimExpiresAt < :now "
            + "ORDER BY r.claimExpiresAt ASC, r.automaticBillingRunId ASC")
    java.util.List<UUID> findInterruptedRunIds(@Param("now") LocalDateTime now,
            org.springframework.data.domain.Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM AutomaticBillingRun r WHERE r.automaticBillingRunId = :runId")
    Optional<AutomaticBillingRun> findByIdForUpdate(@Param("runId") UUID runId);

    /**
     * Atomic claim: one conditional UPDATE, so of any number of concurrent
     * workers exactly one sees {@code 1} - the database row lock decides, not
     * a read-then-write in Java. Claimable are SCHEDULED runs, FAILED runs
     * whose {@code nextRetryAt} (if any) has passed, and PROCESSING runs whose
     * worker's claim has expired. Bypasses {@code @PreUpdate}/{@code @Version},
     * so {@code updatedAt} and {@code version} are maintained here.
     *
     * @return 1 if this caller now owns the run, 0 otherwise
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AutomaticBillingRun r SET "
            + "r.status = com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus.PROCESSING, "
            + "r.attemptCount = r.attemptCount + 1, "
            + "r.claimToken = :claimToken, "
            + "r.claimExpiresAt = :claimExpiresAt, "
            + "r.startedAt = :now, "
            + "r.currentStep = null, "
            + "r.updatedAt = :now, "
            + "r.version = r.version + 1 "
            + "WHERE r.automaticBillingRunId = :runId AND ("
            + "  (r.status IN :claimableStatuses AND (r.nextRetryAt IS NULL OR r.nextRetryAt <= :now)) "
            + "  OR (r.status = com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus.PROCESSING "
            + "      AND r.claimExpiresAt < :now))")
    int claim(@Param("runId") UUID runId,
              @Param("claimToken") UUID claimToken,
              @Param("claimExpiresAt") LocalDateTime claimExpiresAt,
              @Param("now") LocalDateTime now,
              @Param("claimableStatuses") Collection<AutomaticBillingRunStatus> claimableStatuses);
}
