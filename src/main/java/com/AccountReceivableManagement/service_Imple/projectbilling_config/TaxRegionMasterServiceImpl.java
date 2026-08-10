package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.TaxRegionRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxRegionResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRegionMaster;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRegionMasterRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.TaxRegionMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaxRegionMasterServiceImpl implements TaxRegionMasterService {

    private final TaxRegionMasterRepository taxRegionRepository;

    @Override
    public TaxRegionResponseDto createTaxRegion(TaxRegionRequestDto request) {

        if (taxRegionRepository.existsByTaxRegionCodeIgnoreCase(request.getTaxRegionCode())) {
            throw new GlobalExceptionHandler.DuplicateResourceException("Tax Region Code already exists.");
        }

        TaxRegionMaster taxRegion = TaxRegionMaster.builder()
                .taxRegionCode(request.getTaxRegionCode().trim().toUpperCase())
                .taxRegionName(request.getTaxRegionName().trim())
                .taxRegime(request.getTaxRegime().trim())
                .currencyCode(request.getCurrencyCode().trim().toUpperCase())
                .description(request.getDescription())
                .isActive(true)
                .build();

        TaxRegionMaster saved = taxRegionRepository.save(taxRegion);

        return mapToResponse(saved);
    }

    @Override
    public TaxRegionResponseDto updateTaxRegion(UUID taxRegionId,
                                                TaxRegionRequestDto request) {

        TaxRegionMaster taxRegion = taxRegionRepository.findById(taxRegionId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Tax Region not found."));

        if (!taxRegion.getTaxRegionCode().equalsIgnoreCase(request.getTaxRegionCode())
                && taxRegionRepository.existsByTaxRegionCodeIgnoreCase(request.getTaxRegionCode())) {

            throw new GlobalExceptionHandler.DuplicateResourceException("Tax Region Code already exists.");
        }

        taxRegion.setTaxRegionCode(request.getTaxRegionCode().trim().toUpperCase());
        taxRegion.setTaxRegionName(request.getTaxRegionName().trim());
        taxRegion.setTaxRegime(request.getTaxRegime().trim());
        taxRegion.setCurrencyCode(request.getCurrencyCode().trim().toUpperCase());
        taxRegion.setDescription(request.getDescription());

        TaxRegionMaster updated = taxRegionRepository.save(taxRegion);

        return mapToResponse(updated);
    }

    @Override
    public TaxRegionResponseDto getTaxRegionById(UUID taxRegionId) {

        TaxRegionMaster taxRegion = taxRegionRepository.findById(taxRegionId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Tax Region not found."));

        return mapToResponse(taxRegion);
    }

    @Override
    public List<TaxRegionResponseDto> getAllTaxRegions() {

        return taxRegionRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaxRegionResponseDto> getActiveTaxRegions() {

        return taxRegionRepository.findByIsActiveTrueOrderByTaxRegionNameAsc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteTaxRegion(UUID taxRegionId) {

        TaxRegionMaster taxRegion = taxRegionRepository.findById(taxRegionId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Tax Region not found."));

        taxRegion.setIsActive(false);

        taxRegionRepository.save(taxRegion);
    }

    private TaxRegionResponseDto mapToResponse(TaxRegionMaster taxRegion) {

        return TaxRegionResponseDto.builder()
                .taxRegionId(taxRegion.getTaxRegionId())
                .taxRegionCode(taxRegion.getTaxRegionCode())
                .taxRegionName(taxRegion.getTaxRegionName())
                .taxRegime(taxRegion.getTaxRegime())
                .currencyCode(taxRegion.getCurrencyCode())
                .description(taxRegion.getDescription())
                .isActive(taxRegion.getIsActive())
                .createdAt(taxRegion.getCreatedAt())
                .updatedAt(taxRegion.getUpdatedAt())
                .build();
    }
}
