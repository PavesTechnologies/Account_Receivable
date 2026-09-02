package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.TaxConfigurationComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaxConfigurationComponentRepository extends JpaRepository<TaxConfigurationComponent, UUID> {

    List<TaxConfigurationComponent>
    findByTaxConfigurationTaxConfigurationIdAndIsActiveTrue(
            UUID taxConfigurationId
    );

    List<TaxConfigurationComponent>
    findByTaxConfigurationTaxConfigurationId(
            UUID taxConfigurationId
    );

    boolean existsByTaxConfigurationTaxConfigurationIdAndTaxTypeTaxTypeId(
            UUID taxConfigurationId,
            UUID taxTypeId
    );

    boolean existsByTaxConfigurationTaxConfigurationIdAndTaxTypeTaxTypeIdAndTaxConfigurationComponentIdNot(
            UUID taxConfigurationId,
            UUID taxTypeId,
            UUID taxConfigurationComponentId
    );

}
