package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.TaxComponentRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxComponentResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxComponentMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRegimeMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxTypeMaster;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxComponentMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRegimeMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxTypeMasterRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.TaxComponentMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TaxComponentMasterServiceImpl implements TaxComponentMasterService {

    private final TaxComponentMasterRepository taxComponentRepository;
    private final TaxRegimeMasterRepository taxRegimeRepository;
    private final TaxTypeMasterRepository taxTypeRepository;

    @Override
    public TaxComponentResponseDto createTaxComponent(
            TaxComponentRequestDto request
    ) {

        TaxRegimeMaster regime = taxRegimeRepository.findById(
                        request.getTaxRegimeId()
                )
                .orElseThrow(() ->
                        new GlobalExceptionHandler
                                .ResourceNotFoundException(
                                "Tax regime not found."
                        )
                );

        if (!Boolean.TRUE.equals(regime.getIsActive())) {
            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Tax regime is inactive."
            );
        }

        TaxTypeMaster taxType = taxTypeRepository.findById(
                        request.getTaxTypeId()
                )
                .orElseThrow(() ->
                        new GlobalExceptionHandler
                                .ResourceNotFoundException(
                                "Tax type not found."
                        )
                );

        if (!Boolean.TRUE.equals(taxType.getIsActive())) {
            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Tax type is inactive."
            );
        }

        validateDates(request.getEffectiveFrom(), request.getEffectiveTo());

        TaxComponentMaster component = TaxComponentMaster.builder()
                .taxRegime(regime)
                .taxType(taxType)
                .componentCode(request.getComponentCode().trim())
                .componentName(request.getComponentName().trim())
                .description(request.getDescription())
                .inputType(request.getInputType())
                .displayOrder(request.getDisplayOrder())
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .isActive(true)
                .build();

        return mapToResponse(taxComponentRepository.save(component));
    }

    @Override
    public TaxComponentResponseDto updateTaxComponent(
            UUID taxComponentId,
            TaxComponentRequestDto request
    ) {

        TaxComponentMaster component = taxComponentRepository.findById(
                        taxComponentId
                )
                .orElseThrow(() ->
                        new GlobalExceptionHandler
                                .ResourceNotFoundException(
                                "Tax component not found."
                        )
                );

        TaxRegimeMaster regime = taxRegimeRepository.findById(
                        request.getTaxRegimeId()
                )
                .orElseThrow(() ->
                        new GlobalExceptionHandler
                                .ResourceNotFoundException(
                                "Tax regime not found."
                        )
                );

        if (!Boolean.TRUE.equals(regime.getIsActive())) {
            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Tax regime is inactive."
            );
        }

        TaxTypeMaster taxType = taxTypeRepository.findById(
                        request.getTaxTypeId()
                )
                .orElseThrow(() ->
                        new GlobalExceptionHandler
                                .ResourceNotFoundException(
                                "Tax type not found."
                        )
                );

        if (!Boolean.TRUE.equals(taxType.getIsActive())) {
            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Tax type is inactive."
            );
        }

        validateDates(request.getEffectiveFrom(), request.getEffectiveTo());

        component.setTaxRegime(regime);
        component.setTaxType(taxType);
        component.setComponentCode(request.getComponentCode().trim());
        component.setComponentName(request.getComponentName().trim());
        component.setDescription(request.getDescription());
        component.setInputType(request.getInputType());
        component.setDisplayOrder(request.getDisplayOrder());
        component.setEffectiveFrom(request.getEffectiveFrom());
        component.setEffectiveTo(request.getEffectiveTo());

        return mapToResponse(taxComponentRepository.save(component));
    }

    @Override
    @Transactional(readOnly = true)
    public TaxComponentResponseDto getTaxComponentById(UUID taxComponentId) {

        return mapToResponse(
                taxComponentRepository.findById(taxComponentId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "Tax component not found."
                                )
                        )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxComponentResponseDto> getAllTaxComponents() {

        return taxComponentRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxComponentResponseDto> getTaxComponentsByRegime(
            UUID taxRegimeId
    ) {

        if (!taxRegimeRepository.existsById(taxRegimeId)) {
            throw new GlobalExceptionHandler
                    .ResourceNotFoundException(
                    "Tax regime not found."
            );
        }

        return taxComponentRepository
                .findByTaxRegime_TaxRegimeIdAndIsActiveTrueOrderByDisplayOrderAsc(
                        taxRegimeId
                )
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deactivateTaxComponent(UUID taxComponentId) {

        TaxComponentMaster component = taxComponentRepository.findById(
                        taxComponentId
                )
                .orElseThrow(() ->
                        new GlobalExceptionHandler
                                .ResourceNotFoundException(
                                "Tax component not found."
                        )
                );

        component.setIsActive(false);
        taxComponentRepository.save(component);
    }

    private void validateDates(LocalDate from, LocalDate to) {

        if (to != null && to.isBefore(from)) {
            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Effective end date cannot be earlier than effective start date."
            );
        }
    }

    private TaxComponentResponseDto mapToResponse(
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
