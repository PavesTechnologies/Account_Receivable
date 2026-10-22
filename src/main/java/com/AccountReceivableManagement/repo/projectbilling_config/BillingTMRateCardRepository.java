package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTMRateCard;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingTMRateCardRepository extends JpaRepository<BillingTMRateCard, UUID> {
    List<BillingTMRateCard> findByBillingConfigurationAndIsActiveTrueOrderByRoleNameAsc(
            BillingConfiguration billingConfiguration);

    List<BillingTMRateCard> findByBillingConfiguration_BillingConfigurationIdAndIsActiveTrue(UUID billingConfigurationId);

    @Query("SELECT r FROM BillingTMRateCard r WHERE r.billingConfiguration.billingConfigurationId = :billingConfigurationId " +
           "AND r.isActive = true " +
           "AND (r.effectiveFrom IS NULL OR r.effectiveFrom <= :workDate) " +
           "AND (r.effectiveTo IS NULL OR r.effectiveTo >= :workDate) " +
           "ORDER BY r.createdAt DESC")
    List<BillingTMRateCard> findActiveRatesByConfigurationAndDate(
            @Param("billingConfigurationId") UUID billingConfigurationId,
            @Param("workDate") LocalDate workDate);

    boolean existsByBillingConfigurationAndRoleNameIgnoreCaseAndIsActiveTrue(
            BillingConfiguration billingConfiguration,
            String roleName);

    boolean existsByBillingConfiguration(
            BillingConfiguration billingConfiguration);

    boolean existsByBillingConfigurationAndIsActiveTrue(
            BillingConfiguration billingConfiguration);

    long countByBillingConfigurationAndIsActiveTrue(
            BillingConfiguration billingConfiguration);

    void deleteByBillingConfiguration(BillingConfiguration billingConfiguration);

    /**
     * Row lock on the parent billing configuration. Every rate-card writer takes it first, so concurrent
     * creates/updates for one configuration run one after another and cannot both pass the role-overlap check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM BillingConfiguration c WHERE c.billingConfigurationId = :billingConfigurationId")
    Optional<BillingConfiguration> lockConfiguration(@Param("billingConfigurationId") UUID billingConfigurationId);

    /**
     * Active cards of a configuration read with a locking read ({@code FOR UPDATE}), which sees the latest
     * committed rows even under MySQL's REPEATABLE READ snapshot - a plain read could miss a card another
     * transaction has just committed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM BillingTMRateCard r WHERE r.billingConfiguration.billingConfigurationId = :billingConfigurationId " +
           "AND r.isActive = true")
    List<BillingTMRateCard> findActiveByConfigurationForUpdate(
            @Param("billingConfigurationId") UUID billingConfigurationId);

    /**
     * Removes every discount period (active or soft-deleted) of the configuration's rate cards. Needed before the
     * cards themselves are deleted: billing_tm_rate_discount.rate_card_id references billing_tm_rate_card.
     * Only used when deleting a draft configuration that no snapshot references.
     */
    /**
     * READ-ONLY legacy-data diagnostic (not used by application code): pairs of active role-less cards in the same
     * non-Role-Based configuration (Standard or unset pricing model) whose inclusive windows overlap, a null bound
     * being open-ended. Each pair appears once. Run it only against an approved isolated/staging dataset.
     */
    @Query("SELECT new com.AccountReceivableManagement.dto.projectbilling_config.RoleLessRateCardOverlapDto(" +
           "c.billingConfigurationId, c.pricingModel, c.approvalStatus, c.billingStatus, " +
           "a.rateCardId, a.effectiveFrom, a.effectiveTo, b.rateCardId, b.effectiveFrom, b.effectiveTo) " +
           "FROM BillingTMRateCard a, BillingTMRateCard b, BillingConfiguration c " +
           "WHERE a.billingConfiguration = c AND b.billingConfiguration = c AND a.rateCardId < b.rateCardId " +
           "AND a.isActive = true AND b.isActive = true " +
           "AND (a.roleName IS NULL OR TRIM(a.roleName) = '') AND (b.roleName IS NULL OR TRIM(b.roleName) = '') " +
           "AND (c.pricingModel IS NULL OR c.pricingModel <> com.AccountReceivableManagement.entity_enums." +
           "projectbilling_config.PricingModel.ROLE_BASED) " +
           "AND (a.effectiveFrom IS NULL OR b.effectiveTo IS NULL OR a.effectiveFrom <= b.effectiveTo) " +
           "AND (b.effectiveFrom IS NULL OR a.effectiveTo IS NULL OR b.effectiveFrom <= a.effectiveTo) " +
           "ORDER BY c.billingConfigurationId, a.effectiveFrom, b.effectiveFrom")
    List<com.AccountReceivableManagement.dto.projectbilling_config.RoleLessRateCardOverlapDto>
            findOverlappingRoleLessCards();

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM BillingTMRateDiscount d WHERE d.rateCard.rateCardId IN " +
           "(SELECT r.rateCardId FROM BillingTMRateCard r WHERE r.billingConfiguration = :configuration)")
    void deleteDiscountsByConfiguration(@Param("configuration") BillingConfiguration configuration);
}
