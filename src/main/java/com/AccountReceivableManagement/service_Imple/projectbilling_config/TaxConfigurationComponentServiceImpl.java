package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationComponentRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationComponentResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxConfigurationComponent;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxTypeMaster;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxConfigurationComponentRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxTypeMasterRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.TaxConfigurationComponentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TaxConfigurationComponentServiceImpl implements TaxConfigurationComponentService {

    private final TaxConfigurationComponentRepository componentRepository;

    private final TaxConfigurationRepository taxConfigurationRepository;

    private final TaxTypeMasterRepository taxTypeMasterRepository;

    @Override
    public TaxConfigurationComponentResponseDto createComponent(
            UUID taxConfigurationId,
            TaxConfigurationComponentRequestDto request
    ) {

        TaxConfiguration configuration =
                taxConfigurationRepository.findById(taxConfigurationId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Tax configuration not found with ID: "
                                        + taxConfigurationId
                        ));

        if (Boolean.FALSE.equals(configuration.getIsActive())) {
            throw new IllegalArgumentException(
                    "Cannot add component to an inactive tax configuration."
            );
        }

        TaxTypeMaster taxType =
                taxTypeMasterRepository.findById(request.getTaxTypeId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Tax type not found with ID: "
                                        + request.getTaxTypeId()
                        ));

        if (Boolean.FALSE.equals(taxType.getIsActive())) {
            throw new IllegalArgumentException(
                    "Cannot use an inactive tax type."
            );
        }

        boolean alreadyExists =
                componentRepository
                        .existsByTaxConfigurationTaxConfigurationIdAndTaxTypeTaxTypeId(
                                taxConfigurationId,
                                request.getTaxTypeId()
                        );

        if (alreadyExists) {
            throw new IllegalArgumentException(
                    "Tax type '" + taxType.getTaxTypeCode()
                            + "' already exists in this tax configuration."
            );
        }

        validateTaxRate(request.getTaxRate());

        TaxConfigurationComponent component =
                TaxConfigurationComponent.builder()
                        .taxConfiguration(configuration)
                        .taxType(taxType)
                        .taxRate(request.getTaxRate())
                        .applicabilityType(request.getApplicabilityType())
                        .isActive(true)
                        .build();

        TaxConfigurationComponent saved =
                componentRepository.save(component);

        return mapToResponse(saved);
    }

    @Override
    public TaxConfigurationComponentResponseDto updateComponent(
            UUID taxConfigurationComponentId,
            TaxConfigurationComponentRequestDto request
    ) {

        TaxConfigurationComponent component =
                componentRepository.findById(taxConfigurationComponentId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Tax configuration component not found with ID: "
                                        + taxConfigurationComponentId
                        ));

        TaxConfiguration configuration =
                component.getTaxConfiguration();

        if (Boolean.FALSE.equals(configuration.getIsActive())) {
            throw new IllegalArgumentException(
                    "Cannot update a component of an inactive tax configuration."
            );
        }

        TaxTypeMaster taxType =
                taxTypeMasterRepository.findById(request.getTaxTypeId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Tax type not found with ID: "
                                        + request.getTaxTypeId()
                        ));

        if (Boolean.FALSE.equals(taxType.getIsActive())) {
            throw new IllegalArgumentException(
                    "Cannot use an inactive tax type."
            );
        }

        boolean alreadyExists =
                componentRepository
                        .existsByTaxConfigurationTaxConfigurationIdAndTaxTypeTaxTypeIdAndTaxConfigurationComponentIdNot(
                                configuration.getTaxConfigurationId(),
                                request.getTaxTypeId(),
                                taxConfigurationComponentId
                        );

        if (alreadyExists) {
            throw new IllegalArgumentException(
                    "Tax type '" + taxType.getTaxTypeCode()
                            + "' already exists in this tax configuration."
            );
        }

        validateTaxRate(request.getTaxRate());

        component.setTaxType(taxType);
        component.setTaxRate(request.getTaxRate());
        component.setApplicabilityType(request.getApplicabilityType());

        TaxConfigurationComponent updated =
                componentRepository.save(component);

        return mapToResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public TaxConfigurationComponentResponseDto getComponentById(
            UUID taxConfigurationComponentId
    ) {

        TaxConfigurationComponent component =
                componentRepository.findById(taxConfigurationComponentId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Tax configuration component not found with ID: "
                                        + taxConfigurationComponentId
                        ));

        return mapToResponse(component);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxConfigurationComponentResponseDto>
    getComponentsByConfiguration(UUID taxConfigurationId) {

        if (!taxConfigurationRepository.existsById(taxConfigurationId)) {
            throw new IllegalArgumentException(
                    "Tax configuration not found with ID: "
                            + taxConfigurationId
            );
        }

        return componentRepository
                .findByTaxConfigurationTaxConfigurationIdAndIsActiveTrue(
                        taxConfigurationId
                )
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxConfigurationComponentResponseDto>
    getAllComponents() {

        return componentRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public void deactivateComponent(
            UUID taxConfigurationComponentId
    ) {

        TaxConfigurationComponent component =
                componentRepository.findById(taxConfigurationComponentId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Tax configuration component not found with ID: "
                                        + taxConfigurationComponentId
                        ));

        component.setIsActive(false);

        componentRepository.save(component);
    }

    private void validateTaxRate(BigDecimal taxRate) {

        if (taxRate == null) {
            throw new IllegalArgumentException(
                    "Tax rate is required."
            );
        }

        if (taxRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Tax rate cannot be negative."
            );
        }

        if (taxRate.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException(
                    "Tax rate cannot exceed 100%."
            );
        }
    }

    private TaxConfigurationComponentResponseDto mapToResponse(
            TaxConfigurationComponent component
    ) {

        TaxTypeMaster taxType = component.getTaxType();

        return TaxConfigurationComponentResponseDto.builder()
                .taxConfigurationComponentId(
                        component.getTaxConfigurationComponentId()
                )
                .taxTypeId(taxType.getTaxTypeId())
                .taxTypeCode(taxType.getTaxTypeCode())
                .taxTypeName(taxType.getTaxTypeName())
                .taxRate(component.getTaxRate())
                .applicabilityType(component.getApplicabilityType())
                .isActive(component.getIsActive())
                .build();
    }



}
