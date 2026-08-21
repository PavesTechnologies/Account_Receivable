package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.TaxRateConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaxRateConfigurationRepository extends JpaRepository<TaxRateConfiguration, UUID> {

    List<TaxRateConfiguration> findByIsActiveTrueOrderByEffectiveFromDesc();

    List<TaxRateConfiguration> findByTaxRegion_TaxRegionIdAndIsActiveTrueOrderByEffectiveFromDesc(UUID taxRegionId);

    /**
     * Resolves the tax rate configuration applicable to a tax region on a given date.
     * Intended for consumption by the future Tax Calculation phase.
     */
    @Query("SELECT t FROM TaxRateConfiguration t " +
           "WHERE t.taxRegion.taxRegionId = :taxRegionId " +
           "AND t.isActive = true " +
           "AND t.effectiveFrom <= :onDate " +
           "AND (t.effectiveTo IS NULL OR t.effectiveTo >= :onDate) " +
           "ORDER BY t.effectiveFrom DESC")
    List<TaxRateConfiguration> findApplicableConfigurations(
            @Param("taxRegionId") UUID taxRegionId,
            @Param("onDate") LocalDate onDate);

    @Query("SELECT t FROM TaxRateConfiguration t " +
           "WHERE t.taxRegion.taxRegionId = :taxRegionId " +
           "AND t.isActive = true " +
           "AND (:excludeId IS NULL OR t.taxRateConfigurationId <> :excludeId) " +
           "AND (t.effectiveTo IS NULL OR t.effectiveTo >= :effectiveFrom) " +
           "AND t.effectiveFrom <= :effectiveToForCompare")
    List<TaxRateConfiguration> findOverlappingConfigurations(
            @Param("taxRegionId") UUID taxRegionId,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveToForCompare") LocalDate effectiveToForCompare,
            @Param("excludeId") UUID excludeId);
}
