package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.TaxRegionMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxRegionMasterRepository extends JpaRepository<TaxRegionMaster, UUID> {

    Optional<TaxRegionMaster> findByTaxRegionCodeIgnoreCase(String taxRegionCode);

    boolean existsByTaxRegionCodeIgnoreCase(String taxRegionCode);

    List<TaxRegionMaster> findByIsActiveTrueOrderByTaxRegionNameAsc();
}
