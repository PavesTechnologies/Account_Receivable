package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.TaxTypeMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxTypeMasterRepository extends JpaRepository<TaxTypeMaster, UUID> {

    Optional<TaxTypeMaster> findByTaxTypeCodeIgnoreCase(String taxTypeCode);

    boolean existsByTaxTypeCodeIgnoreCase(String taxTypeCode);

    boolean existsByTaxTypeCodeIgnoreCaseAndTaxTypeIdNot(
            String taxTypeCode,
            UUID taxTypeId
    );

    List<TaxTypeMaster> findAllByOrderByTaxTypeNameAsc();

    List<TaxTypeMaster> findByIsActiveTrueOrderByTaxTypeNameAsc();
}
