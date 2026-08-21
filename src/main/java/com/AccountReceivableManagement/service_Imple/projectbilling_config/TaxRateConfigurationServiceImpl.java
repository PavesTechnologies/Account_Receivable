package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.TaxRateConfigurationRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxRateConfigurationResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRateConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRegionMaster;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRateConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRegionMasterRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.TaxRateConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TaxRateConfigurationServiceImpl implements TaxRateConfigurationService {

    /**
     * Stand-in for "infinity" when comparing an open-ended (null effectiveTo) period
     * against stored rows for overlap detection.
     */
    private static final LocalDate OPEN_ENDED_COMPARISON_DATE = LocalDate.of(9999, 12, 31);

    private final TaxRateConfigurationRepository taxRateConfigurationRepository;
    private final TaxRegionMasterRepository taxRegionMasterRepository;

    @Override
    public TaxRateConfigurationResponseDto create(TaxRateConfigurationRequestDto request) {

        TaxRegionMaster taxRegion = resolveActiveTaxRegion(request.getTaxRegionId());

        validateRates(request);
        validateEffectiveDates(request.getEffectiveFrom(), request.getEffectiveTo());
        validateNoOverlap(taxRegion.getTaxRegionId(), request.getEffectiveFrom(), request.getEffectiveTo(), null);

        TaxRateConfiguration configuration = TaxRateConfiguration.builder()
                .taxRegion(taxRegion)
                .taxType(request.getTaxType().trim())
                .cgstRate(request.getCgstRate())
                .sgstRate(request.getSgstRate())
                .igstRate(request.getIgstRate())
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .isActive(true)
                .build();

        TaxRateConfiguration saved = taxRateConfigurationRepository.save(configuration);

        return mapToResponse(saved);
    }

    @Override
    public TaxRateConfigurationResponseDto update(UUID taxRateConfigurationId, TaxRateConfigurationRequestDto request) {

        TaxRateConfiguration configuration = taxRateConfigurationRepository.findById(taxRateConfigurationId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Tax rate configuration not found."));

        TaxRegionMaster taxRegion = resolveActiveTaxRegion(request.getTaxRegionId());

        validateRates(request);
        validateEffectiveDates(request.getEffectiveFrom(), request.getEffectiveTo());
        validateNoOverlap(taxRegion.getTaxRegionId(), request.getEffectiveFrom(), request.getEffectiveTo(), taxRateConfigurationId);

        configuration.setTaxRegion(taxRegion);
        configuration.setTaxType(request.getTaxType().trim());
        configuration.setCgstRate(request.getCgstRate());
        configuration.setSgstRate(request.getSgstRate());
        configuration.setIgstRate(request.getIgstRate());
        configuration.setEffectiveFrom(request.getEffectiveFrom());
        configuration.setEffectiveTo(request.getEffectiveTo());

        TaxRateConfiguration updated = taxRateConfigurationRepository.save(configuration);

        return mapToResponse(updated);
    }

    @Override
    public TaxRateConfigurationResponseDto getById(UUID taxRateConfigurationId) {

        TaxRateConfiguration configuration = taxRateConfigurationRepository.findById(taxRateConfigurationId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Tax rate configuration not found."));

        return mapToResponse(configuration);
    }

    @Override
    public List<TaxRateConfigurationResponseDto> getAll() {

        return taxRateConfigurationRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaxRateConfigurationResponseDto> getActive() {

        return taxRateConfigurationRepository.findByIsActiveTrueOrderByEffectiveFromDesc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaxRateConfigurationResponseDto> getByTaxRegion(UUID taxRegionId) {

        taxRegionMasterRepository.findById(taxRegionId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Tax region could not be found."));

        return taxRateConfigurationRepository
                .findByTaxRegion_TaxRegionIdAndIsActiveTrueOrderByEffectiveFromDesc(taxRegionId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deactivate(UUID taxRateConfigurationId) {

        TaxRateConfiguration configuration = taxRateConfigurationRepository.findById(taxRateConfigurationId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Tax rate configuration not found."));

        configuration.setIsActive(false);

        taxRateConfigurationRepository.save(configuration);
    }

    private TaxRegionMaster resolveActiveTaxRegion(UUID taxRegionId) {

        TaxRegionMaster taxRegion = taxRegionMasterRepository.findById(taxRegionId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Tax region could not be found."));

        if (!Boolean.TRUE.equals(taxRegion.getIsActive())) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Tax region is inactive and cannot be used for a tax rate configuration.");
        }

        return taxRegion;
    }

    private void validateRates(TaxRateConfigurationRequestDto request) {

        if (isNegative(request.getCgstRate()) || isNegative(request.getSgstRate()) || isNegative(request.getIgstRate())) {
            throw new GlobalExceptionHandler.ValidationException("Tax rates cannot be negative.");
        }

        if (request.getCgstRate() == null && request.getSgstRate() == null && request.getIgstRate() == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "At least one applicable tax component (CGST, SGST or IGST) must be configured.");
        }
    }

    private boolean isNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) < 0;
    }

    private void validateEffectiveDates(LocalDate effectiveFrom, LocalDate effectiveTo) {

        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Effective end date cannot be earlier than effective start date.");
        }
    }

    private void validateNoOverlap(UUID taxRegionId, LocalDate effectiveFrom, LocalDate effectiveTo, UUID excludeId) {

        LocalDate effectiveToForCompare = effectiveTo != null ? effectiveTo : OPEN_ENDED_COMPARISON_DATE;

        List<TaxRateConfiguration> overlapping = taxRateConfigurationRepository.findOverlappingConfigurations(
                taxRegionId, effectiveFrom, effectiveToForCompare, excludeId);

        if (!overlapping.isEmpty()) {
            throw new GlobalExceptionHandler.ValidationException(
                    "An active tax configuration already exists for this tax region and effective period.");
        }
    }

    private TaxRateConfigurationResponseDto mapToResponse(TaxRateConfiguration configuration) {

        TaxRegionMaster taxRegion = configuration.getTaxRegion();

        return TaxRateConfigurationResponseDto.builder()
                .taxRateConfigurationId(configuration.getTaxRateConfigurationId())
                .taxRegionId(taxRegion.getTaxRegionId())
                .taxRegionCode(taxRegion.getTaxRegionCode())
                .taxRegionName(taxRegion.getTaxRegionName())
                .taxRegime(taxRegion.getTaxRegime())
                .taxType(configuration.getTaxType())
                .cgstRate(configuration.getCgstRate())
                .sgstRate(configuration.getSgstRate())
                .igstRate(configuration.getIgstRate())
                .effectiveFrom(configuration.getEffectiveFrom())
                .effectiveTo(configuration.getEffectiveTo())
                .isActive(configuration.getIsActive())
                .createdAt(configuration.getCreatedAt())
                .updatedAt(configuration.getUpdatedAt())
                .build();
    }
}
