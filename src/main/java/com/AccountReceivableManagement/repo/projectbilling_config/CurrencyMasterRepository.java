package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.CurrencyMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CurrencyMasterRepository extends JpaRepository<CurrencyMaster, UUID> {
    Optional<CurrencyMaster> findByCurrencyCodeIgnoreCase(String currencyCode);

    Optional<CurrencyMaster> findByCurrencyNameIgnoreCase(String currencyName);

    List<CurrencyMaster> findByIsActiveTrueOrderByCurrencyCodeAsc();

    boolean existsByCurrencyCodeIgnoreCase(String currencyCode);

    boolean existsByCurrencyNameIgnoreCase(String currencyName);
}
