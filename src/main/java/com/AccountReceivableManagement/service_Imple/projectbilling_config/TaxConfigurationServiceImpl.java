package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationComponentRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationComponentResponseDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxConfigurationComponent;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRegionMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxTypeMaster;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRegionMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxTypeMasterRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.TaxConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TaxConfigurationServiceImpl implements TaxConfigurationService {

    /**
     * Stand-in for "infinity" when comparing an open-ended (null effectiveTo) period
     * against stored rows for overlap detection.
     */
    private static final LocalDate OPEN_ENDED_DATE =
            LocalDate.of(9999, 12, 31);

    private final TaxConfigurationRepository configurationRepository;
    private final TaxRegionMasterRepository taxRegionRepository;
    private final TaxTypeMasterRepository taxTypeRepository;

    @Override
    public TaxConfigurationResponseDto create(
            TaxConfigurationRequestDto request
    ) {

        TaxRegionMaster region =
                resolveActiveRegion(request.getTaxRegionId());

        validateDates(
                request.getEffectiveFrom(),
                request.getEffectiveTo()
        );

        validateComponents(request.getComponents());

        validateNoOverlap(
                region.getTaxRegionId(),
                request.getEffectiveFrom(),
                request.getEffectiveTo(),
                null
        );

        TaxConfiguration configuration =
                TaxConfiguration.builder()
                        .taxRegion(region)
                        .taxRegime(
                                request.getTaxRegime().trim()
                        )
                        .effectiveFrom(
                                request.getEffectiveFrom()
                        )
                        .effectiveTo(
                                request.getEffectiveTo()
                        )
                        .isActive(true)
                        .build();

        addComponents(
                configuration,
                request.getComponents()
        );

        return mapToResponse(
                configurationRepository.save(configuration)
        );
    }

    @Override
    public TaxConfigurationResponseDto update(
            UUID id,
            TaxConfigurationRequestDto request
    ) {

        TaxConfiguration configuration =
                configurationRepository.findById(id)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "Tax configuration not found."
                                )
                        );

        TaxRegionMaster region =
                resolveActiveRegion(request.getTaxRegionId());

        validateDates(
                request.getEffectiveFrom(),
                request.getEffectiveTo()
        );

        validateComponents(request.getComponents());

        validateNoOverlap(
                region.getTaxRegionId(),
                request.getEffectiveFrom(),
                request.getEffectiveTo(),
                id
        );

        configuration.setTaxRegion(region);

        configuration.setTaxRegime(
                request.getTaxRegime().trim()
        );

        configuration.setEffectiveFrom(
                request.getEffectiveFrom()
        );

        configuration.setEffectiveTo(
                request.getEffectiveTo()
        );

        configuration.getComponents().clear();

        addComponents(
                configuration,
                request.getComponents()
        );

        return mapToResponse(
                configurationRepository.save(configuration)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public TaxConfigurationResponseDto getById(UUID id) {

        return mapToResponse(
                configurationRepository.findById(id)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "Tax configuration not found."
                                )
                        )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxConfigurationResponseDto> getAll() {

        return configurationRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxConfigurationResponseDto> getActive() {

        return configurationRepository
                .findByIsActiveTrueOrderByEffectiveFromDesc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxConfigurationResponseDto> getByTaxRegion(
            UUID taxRegionId
    ) {

        if (!taxRegionRepository.existsById(taxRegionId)) {
            throw new GlobalExceptionHandler
                    .ResourceNotFoundException(
                    "Tax region not found."
            );
        }

        return configurationRepository
                .findByTaxRegion_TaxRegionIdAndIsActiveTrueOrderByEffectiveFromDesc(
                        taxRegionId
                )
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deactivate(UUID id) {

        TaxConfiguration configuration =
                configurationRepository.findById(id)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "Tax configuration not found."
                                )
                        );

        configuration.setIsActive(false);

        configurationRepository.save(configuration);
    }

    private void addComponents(
            TaxConfiguration configuration,
            List<TaxConfigurationComponentRequestDto> requests
    ) {

        for (TaxConfigurationComponentRequestDto request : requests) {

            TaxTypeMaster taxType =
                    taxTypeRepository.findById(
                                    request.getTaxTypeId()
                            )
                            .orElseThrow(() ->
                                    new GlobalExceptionHandler
                                            .ResourceNotFoundException(
                                            "Tax type not found."
                                    )
                            );

            if (!Boolean.TRUE.equals(
                    taxType.getIsActive()
            )) {

                throw new GlobalExceptionHandler
                        .ValidationException(
                        "Tax type is inactive."
                );
            }

            TaxConfigurationComponent component =
                    TaxConfigurationComponent.builder()
                            .taxConfiguration(configuration)
                            .taxType(taxType)
                            .taxRate(request.getTaxRate())
                            .applicabilityType(
                                    request.getApplicabilityType()
                            )
                            .isActive(true)
                            .build();

            configuration.getComponents()
                    .add(component);
        }
    }

    private void validateComponents(
            List<TaxConfigurationComponentRequestDto>
                    components
    ) {

        if (components == null || components.isEmpty()) {
            throw new GlobalExceptionHandler
                    .ValidationException(
                    "At least one tax component is required."
            );
        }

        Set<UUID> typeIds = new HashSet<>();

        for (TaxConfigurationComponentRequestDto component
                : components) {

            if (!typeIds.add(component.getTaxTypeId())) {

                throw new GlobalExceptionHandler
                        .ValidationException(
                        "A tax type cannot be configured more than once in the same configuration."
                );
            }

            if (component.getTaxRate()
                    .compareTo(BigDecimal.ZERO) < 0) {

                throw new GlobalExceptionHandler
                        .ValidationException(
                        "Tax rate cannot be negative."
                );
            }
        }
    }

    private TaxRegionMaster resolveActiveRegion(UUID id) {

        TaxRegionMaster region =
                taxRegionRepository.findById(id)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "Tax region not found."
                                )
                        );

        if (!Boolean.TRUE.equals(region.getIsActive())) {
            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Tax region is inactive."
            );
        }

        return region;
    }

    private void validateDates(
            LocalDate from,
            LocalDate to
    ) {

        if (to != null && to.isBefore(from)) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Effective end date cannot be earlier than effective start date."
            );
        }
    }

    private void validateNoOverlap(
            UUID regionId,
            LocalDate from,
            LocalDate to,
            UUID excludeId
    ) {

        LocalDate compareTo =
                to != null ? to : OPEN_ENDED_DATE;

        if (!configurationRepository
                .findOverlappingConfigurations(
                        regionId,
                        from,
                        compareTo,
                        excludeId
                )
                .isEmpty()) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    "An active tax configuration already exists for this tax region and effective period."
            );
        }
    }

    private TaxConfigurationResponseDto mapToResponse(
            TaxConfiguration configuration
    ) {

        TaxRegionMaster region =
                configuration.getTaxRegion();

        List<TaxConfigurationComponentResponseDto>
                components =
                configuration.getComponents()
                        .stream()
                        .map(component ->
                                TaxConfigurationComponentResponseDto
                                        .builder()
                                        .taxConfigurationComponentId(
                                                component.getTaxConfigurationComponentId()
                                        )
                                        .taxTypeId(
                                                component.getTaxType().getTaxTypeId()
                                        )
                                        .taxTypeCode(
                                                component.getTaxType().getTaxTypeCode()
                                        )
                                        .taxTypeName(
                                                component.getTaxType().getTaxTypeName()
                                        )
                                        .taxRate(
                                                component.getTaxRate()
                                        )
                                        .applicabilityType(
                                                component.getApplicabilityType()
                                        )
                                        .isActive(
                                                component.getIsActive()
                                        )
                                        .build()
                        )
                        .collect(Collectors.toList());

        return TaxConfigurationResponseDto.builder()
                .taxConfigurationId(
                        configuration.getTaxConfigurationId()
                )
                .taxRegionId(
                        region.getTaxRegionId()
                )
                .taxRegionCode(
                        region.getTaxRegionCode()
                )
                .taxRegionName(
                        region.getTaxRegionName()
                )
                .taxRegime(
                        configuration.getTaxRegime()
                )
                .effectiveFrom(
                        configuration.getEffectiveFrom()
                )
                .effectiveTo(
                        configuration.getEffectiveTo()
                )
                .isActive(
                        configuration.getIsActive()
                )
                .components(components)
                .createdAt(
                        configuration.getCreatedAt()
                )
                .updatedAt(
                        configuration.getUpdatedAt()
                )
                .build();
    }

}
