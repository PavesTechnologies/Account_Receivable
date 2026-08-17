package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTMRateCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
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
}
