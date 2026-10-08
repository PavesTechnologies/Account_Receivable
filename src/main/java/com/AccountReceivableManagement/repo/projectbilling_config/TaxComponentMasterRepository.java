package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.TaxComponentMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxComponentMasterRepository extends JpaRepository<TaxComponentMaster, UUID> {

    List<TaxComponentMaster> findByTaxRegime_TaxRegimeIdAndIsActiveTrueOrderByDisplayOrderAsc(
            UUID taxRegimeId
    );

    @Query("""
        SELECT t
        FROM TaxComponentMaster t
        WHERE t.taxRegime.taxRegimeId = :taxRegimeId
          AND t.isActive = true
          AND t.effectiveFrom <= :onDate
          AND (
              t.effectiveTo IS NULL
              OR t.effectiveTo >= :onDate
          )
        ORDER BY t.displayOrder ASC
    """)
    List<TaxComponentMaster> findActiveComponentsByRegime(
            @Param("taxRegimeId") UUID taxRegimeId,
            @Param("onDate") LocalDate onDate
    );

    Optional<TaxComponentMaster> findByTaxRegime_TaxRegimeIdAndTaxType_TaxTypeIdAndIsActiveTrue(
            UUID taxRegimeId,
            UUID taxTypeId
    );
}
