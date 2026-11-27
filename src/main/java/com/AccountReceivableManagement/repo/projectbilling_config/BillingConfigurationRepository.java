package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.CurrencyMaster;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingConfigurationRepository extends JpaRepository<BillingConfiguration, UUID> {

    Optional<BillingConfiguration>
    findByProject_PmsProjectIdAndApprovalStatusAndBillingStatus(
            Long projectId,
            ApprovalStatus approvalStatus,
            BillingConfigurationStatus billingStatus
    );

    boolean existsByProject_PmsProjectIdAndApprovalStatusAndBillingStatus(
            Long projectId,
            ApprovalStatus approvalStatus,
            BillingConfigurationStatus billingStatus
    );

    List<BillingConfiguration>
    findByApprovalStatus(ApprovalStatus approvalStatus);

    List<BillingConfiguration>
    findByBillingStatus(BillingConfigurationStatus billingStatus);

    List<BillingConfiguration>
    findByApprovalStatusAndBillingStatus(
            ApprovalStatus approvalStatus,
            BillingConfigurationStatus billingStatus
    );

    List<BillingConfiguration>
    findByClientClientId(UUID clientId);


    List<BillingConfiguration>
    findByProjectPmsProjectId(Long projectId);

    /**
     * One page of the Billing Data Acquisition listing: approved configurations in
     * the given billing status, newest project start date first.
     *
     * The ordering lives in the query rather than in the {@code Pageable}'s
     * {@code Sort} so that it cannot interact with the fetch joins, and so the
     * null handling is explicit: MySQL and H2 disagree on where NULLs land in a
     * DESC order, and a project without a start date must never head the list.
     * {@code billingConfigurationId} breaks ties - project start dates are not
     * unique, and without a deterministic total order rows duplicate or disappear
     * across page boundaries.
     *
     * The fetch joins are all to-one, so they do not multiply rows and pagination
     * still happens in SQL. Never add a to-many fetch join here: Hibernate would
     * silently fall back to paginating in memory (HHH000104).
     */
    @Query(value = "SELECT c FROM BillingConfiguration c "
            + "JOIN FETCH c.project p "
            + "JOIN FETCH c.client "
            + "JOIN FETCH c.billingType "
            + "WHERE c.approvalStatus = :approvalStatus "
            + "AND c.billingStatus = :billingStatus "
            + "ORDER BY CASE WHEN p.startDate IS NULL THEN 1 ELSE 0 END, "
            + "p.startDate DESC, c.billingConfigurationId ASC",
            countQuery = "SELECT COUNT(c) FROM BillingConfiguration c "
            + "WHERE c.approvalStatus = :approvalStatus "
            + "AND c.billingStatus = :billingStatus")
    org.springframework.data.domain.Page<BillingConfiguration> findAcquisitionOverviewPage(
            @org.springframework.data.repository.query.Param("approvalStatus") ApprovalStatus approvalStatus,
            @org.springframework.data.repository.query.Param("billingStatus") BillingConfigurationStatus billingStatus,
            org.springframework.data.domain.Pageable pageable);

    /**
     * As {@link #findAcquisitionOverviewPage}, restricted to configurations whose
     * billing type master name is one of {@code lowerCaseBillingTypeNames}.
     *
     * The billing type is matched on the stored master name, not on
     * {@code BillingType}: that enum is derived at read time and is never
     * persisted. Filtering has to happen here rather than in Java, or
     * {@code totalElements} would count rows the caller filtered out.
     */
    @Query(value = "SELECT c FROM BillingConfiguration c "
            + "JOIN FETCH c.project p "
            + "JOIN FETCH c.client "
            + "JOIN FETCH c.billingType bt "
            + "WHERE c.approvalStatus = :approvalStatus "
            + "AND c.billingStatus = :billingStatus "
            + "AND LOWER(TRIM(bt.billingTypeName)) IN :lowerCaseBillingTypeNames "
            + "ORDER BY CASE WHEN p.startDate IS NULL THEN 1 ELSE 0 END, "
            + "p.startDate DESC, c.billingConfigurationId ASC",
            countQuery = "SELECT COUNT(c) FROM BillingConfiguration c "
            + "WHERE c.approvalStatus = :approvalStatus "
            + "AND c.billingStatus = :billingStatus "
            + "AND LOWER(TRIM(c.billingType.billingTypeName)) IN :lowerCaseBillingTypeNames")
    org.springframework.data.domain.Page<BillingConfiguration> findAcquisitionOverviewPageByBillingTypeNames(
            @org.springframework.data.repository.query.Param("approvalStatus") ApprovalStatus approvalStatus,
            @org.springframework.data.repository.query.Param("billingStatus") BillingConfigurationStatus billingStatus,
            @org.springframework.data.repository.query.Param("lowerCaseBillingTypeNames")
            java.util.Collection<String> lowerCaseBillingTypeNames,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Every configuration of the project, row-locked, in a fixed order. A
     * locking read sees the latest committed state and serializes concurrent
     * approvals on the same project, so two of them cannot both pass the
     * one-live-configuration check.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM BillingConfiguration c WHERE c.project.pmsProjectId = :projectId "
            + "ORDER BY c.billingConfigurationId")
    List<BillingConfiguration> findByProjectIdForUpdate(
            @org.springframework.data.repository.query.Param("projectId") Long projectId);

    /**
     * Approved, AUTOMATIC, not manually deactivated configurations whose
     * billing type is one of {@code lowerCaseBillingTypeNames} - the automatic
     * billing scheduler's Time &amp; Material configurations.
     */
    @Query("SELECT c.billingConfigurationId FROM BillingConfiguration c "
            + "WHERE c.approvalStatus = :approvalStatus "
            + "AND c.invoiceGenerationType = :invoiceGenerationType "
            + "AND c.manuallyDeactivated = false "
            + "AND LOWER(TRIM(c.billingType.billingTypeName)) IN :lowerCaseBillingTypeNames "
            + "ORDER BY c.billingConfigurationId")
    List<UUID> findAutomaticConfigurationIdsByBillingTypeNames(
            @org.springframework.data.repository.query.Param("approvalStatus") ApprovalStatus approvalStatus,
            @org.springframework.data.repository.query.Param("invoiceGenerationType")
            com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType invoiceGenerationType,
            @org.springframework.data.repository.query.Param("lowerCaseBillingTypeNames")
            java.util.Collection<String> lowerCaseBillingTypeNames);

    /**
     * As {@link #findAutomaticConfigurationIdsByBillingTypeNames}, limited to
     * configurations that can have a billing period due in
     * {@code earliestBillingDate}..{@code today} (G-29): started by today, and
     * not ended before the window - a T&amp;M period never ends (is billed)
     * after Effective To. Configurations without dates are kept, so the
     * resolver still reports them as misconfigured.
     */
    @Query("SELECT c.billingConfigurationId FROM BillingConfiguration c "
            + "WHERE c.approvalStatus = :approvalStatus "
            + "AND c.invoiceGenerationType = :invoiceGenerationType "
            + "AND c.manuallyDeactivated = false "
            + "AND LOWER(TRIM(c.billingType.billingTypeName)) IN :lowerCaseBillingTypeNames "
            + "AND (c.effectiveFrom IS NULL OR c.effectiveFrom <= :today) "
            + "AND (c.effectiveTo IS NULL OR c.effectiveTo >= :earliestBillingDate) "
            + "ORDER BY c.billingConfigurationId")
    List<UUID> findAutomaticConfigurationIdsByBillingTypeNamesDueBetween(
            @org.springframework.data.repository.query.Param("approvalStatus") ApprovalStatus approvalStatus,
            @org.springframework.data.repository.query.Param("invoiceGenerationType")
            com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType invoiceGenerationType,
            @org.springframework.data.repository.query.Param("lowerCaseBillingTypeNames")
            java.util.Collection<String> lowerCaseBillingTypeNames,
            @org.springframework.data.repository.query.Param("earliestBillingDate") java.time.LocalDate earliestBillingDate,
            @org.springframework.data.repository.query.Param("today") java.time.LocalDate today);
}
