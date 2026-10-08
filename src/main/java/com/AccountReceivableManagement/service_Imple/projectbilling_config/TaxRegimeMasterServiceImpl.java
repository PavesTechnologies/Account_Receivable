package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.TaxComponentResponseDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxRegimeRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxRegimeResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxComponentMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRegimeMaster;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRegimeMasterRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.TaxRegimeMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TaxRegimeMasterServiceImpl implements TaxRegimeMasterService {

    private final TaxRegimeMasterRepository taxRegimeRepository;

    @Override
    public TaxRegimeResponseDto createTaxRegime(TaxRegimeRequestDto request) {

        if (taxRegimeRepository.existsByTaxRegimeCodeIgnoreCase(
                request.getTaxRegimeCode().trim()
        )) {
            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Tax regime code already exists."
            );
        }

        TaxRegimeMaster regime = TaxRegimeMaster.builder()
                .taxRegimeCode(request.getTaxRegimeCode().trim())
                .taxRegimeName(request.getTaxRegimeName().trim())
                .description(request.getDescription())
                .isActive(true)
                .build();

        return mapToResponse(taxRegimeRepository.save(regime));
    }

    @Override
    public TaxRegimeResponseDto updateTaxRegime(
            UUID taxRegimeId,
            TaxRegimeRequestDto request
    ) {

        TaxRegimeMaster regime = taxRegimeRepository.findById(taxRegimeId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler
                                .ResourceNotFoundException(
                                "Tax regime not found."
                        )
                );

        if (taxRegimeRepository.existsByTaxRegimeCodeIgnoreCaseAndTaxRegimeIdNot(
                request.getTaxRegimeCode().trim(),
                taxRegimeId
        )) {
            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Tax regime code already exists."
            );
        }

        regime.setTaxRegimeCode(request.getTaxRegimeCode().trim());
        regime.setTaxRegimeName(request.getTaxRegimeName().trim());
        regime.setDescription(request.getDescription());

        return mapToResponse(taxRegimeRepository.save(regime));
    }

    @Override
    @Transactional(readOnly = true)
    public TaxRegimeResponseDto getTaxRegimeById(UUID taxRegimeId) {

        return mapToResponse(
                taxRegimeRepository.findById(taxRegimeId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "Tax regime not found."
                                )
                        )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxRegimeResponseDto> getAllTaxRegimes() {

        return taxRegimeRepository.findAllByOrderByTaxRegimeNameAsc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxRegimeResponseDto> getActiveTaxRegimes() {

        return taxRegimeRepository.findByIsActiveTrueOrderByTaxRegimeNameAsc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deactivateTaxRegime(UUID taxRegimeId) {

        TaxRegimeMaster regime = taxRegimeRepository.findById(taxRegimeId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler
                                .ResourceNotFoundException(
                                "Tax regime not found."
                        )
                );

        regime.setIsActive(false);
        taxRegimeRepository.save(regime);
    }

    private TaxRegimeResponseDto mapToResponse(TaxRegimeMaster regime) {

        List<TaxComponentResponseDto> components =
                regime.getComponents()
                        .stream()
                        .map(this::mapComponentToResponse)
                        .collect(Collectors.toList());

        return TaxRegimeResponseDto.builder()
                .taxRegimeId(regime.getTaxRegimeId())
                .taxRegimeCode(regime.getTaxRegimeCode())
                .taxRegimeName(regime.getTaxRegimeName())
                .description(regime.getDescription())
                .isActive(regime.getIsActive())
                .components(components)
                .createdAt(regime.getCreatedAt())
                .updatedAt(regime.getUpdatedAt())
                .build();
    }

    private TaxComponentResponseDto mapComponentToResponse(
            TaxComponentMaster component
    ) {

        return TaxComponentResponseDto.builder()
                .taxComponentId(component.getTaxComponentId())
                .taxRegimeId(component.getTaxRegime().getTaxRegimeId())
                .taxRegimeCode(component.getTaxRegime().getTaxRegimeCode())
                .taxRegimeName(component.getTaxRegime().getTaxRegimeName())
                .taxTypeId(component.getTaxType().getTaxTypeId())
                .taxTypeCode(component.getTaxType().getTaxTypeCode())
                .taxTypeName(component.getTaxType().getTaxTypeName())
                .componentCode(component.getComponentCode())
                .componentName(component.getComponentName())
                .description(component.getDescription())
                .inputType(component.getInputType())
                .displayOrder(component.getDisplayOrder())
                .effectiveFrom(component.getEffectiveFrom())
                .effectiveTo(component.getEffectiveTo())
                .isActive(component.getIsActive())
                .createdAt(component.getCreatedAt())
                .updatedAt(component.getUpdatedAt())
                .build();
    }
}
