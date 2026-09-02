package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.TaxConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaxConfigurationRepository extends JpaRepository<TaxConfiguration, UUID> {

    List<TaxConfiguration>
    findByIsActiveTrueOrderByEffectiveFromDesc();

    List<TaxConfiguration>
    findByTaxRegion_TaxRegionIdAndIsActiveTrueOrderByEffectiveFromDesc(
            UUID taxRegionId
    );

    @Query("""
        SELECT t
        FROM TaxConfiguration t
        WHERE t.taxRegion.taxRegionId = :taxRegionId
          AND t.isActive = true
          AND t.effectiveFrom <= :onDate
          AND (
              t.effectiveTo IS NULL
              OR t.effectiveTo >= :onDate
          )
        ORDER BY t.effectiveFrom DESC
    """)
    List<TaxConfiguration> findApplicableConfigurations(
            @Param("taxRegionId") UUID taxRegionId,
            @Param("onDate") LocalDate onDate
    );

    @Query("""
        SELECT t
        FROM TaxConfiguration t
        WHERE t.taxRegion.taxRegionId = :taxRegionId
          AND t.isActive = true
          AND (
              :excludeId IS NULL
              OR t.taxConfigurationId <> :excludeId
          )
          AND (
              t.effectiveTo IS NULL
              OR t.effectiveTo >= :effectiveFrom
          )
          AND t.effectiveFrom <= :effectiveToForCompare
    """)
    List<TaxConfiguration> findOverlappingConfigurations(
            @Param("taxRegionId") UUID taxRegionId,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveToForCompare") LocalDate effectiveToForCompare,
            @Param("excludeId") UUID excludeId
    );
}
