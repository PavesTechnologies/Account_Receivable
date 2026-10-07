package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.TaxRegimeMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxRegimeMasterRepository extends JpaRepository<TaxRegimeMaster, UUID> {

    Optional<TaxRegimeMaster> findByTaxRegimeCodeIgnoreCase(String taxRegimeCode);

    boolean existsByTaxRegimeCodeIgnoreCase(String taxRegimeCode);

    boolean existsByTaxRegimeCodeIgnoreCaseAndTaxRegimeIdNot(
            String taxRegimeCode,
            UUID taxRegimeId
    );

    List<TaxRegimeMaster> findAllByOrderByTaxRegimeNameAsc();

    List<TaxRegimeMaster> findByIsActiveTrueOrderByTaxRegimeNameAsc();
}
