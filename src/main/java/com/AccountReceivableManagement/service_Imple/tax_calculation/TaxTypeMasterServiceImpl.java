package com.AccountReceivableManagement.service_Imple.tax_calculation;

import com.AccountReceivableManagement.dto.tax_calculation.TaxTypeRequestDto;
import com.AccountReceivableManagement.dto.tax_calculation.TaxTypeResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxTypeMaster;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxTypeMasterRepository;
import com.AccountReceivableManagement.service_interface.tax_calculation.TaxTypeMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TaxTypeMasterServiceImpl implements TaxTypeMasterService {

    private final TaxTypeMasterRepository taxTypeMasterRepository;

    @Override
    public TaxTypeResponseDto createTaxType(TaxTypeRequestDto request) {

        String taxTypeCode = request.getTaxTypeCode()
                .trim()
                .toUpperCase();

        if (taxTypeMasterRepository.existsByTaxTypeCodeIgnoreCase(taxTypeCode)) {
            throw new IllegalArgumentException(
                    "Tax type with code '" + taxTypeCode + "' already exists"
            );
        }

        TaxTypeMaster taxType = TaxTypeMaster.builder()
                .taxTypeCode(taxTypeCode)
                .taxTypeName(request.getTaxTypeName().trim())
                .description(
                        request.getDescription() != null
                                ? request.getDescription().trim()
                                : null
                )
                .isActive(true)
                .build();

        TaxTypeMaster savedTaxType =
                taxTypeMasterRepository.save(taxType);

        return mapToResponse(savedTaxType);
    }

    @Override
    public TaxTypeResponseDto updateTaxType(
            UUID taxTypeId,
            TaxTypeRequestDto request
    ) {

        TaxTypeMaster taxType = taxTypeMasterRepository.findById(taxTypeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tax type not found with ID: " + taxTypeId
                ));

        String taxTypeCode = request.getTaxTypeCode()
                .trim()
                .toUpperCase();

        if (taxTypeMasterRepository
                .existsByTaxTypeCodeIgnoreCaseAndTaxTypeIdNot(
                        taxTypeCode,
                        taxTypeId
                )) {

            throw new IllegalArgumentException(
                    "Tax type with code '" + taxTypeCode + "' already exists"
            );
        }

        taxType.setTaxTypeCode(taxTypeCode);
        taxType.setTaxTypeName(request.getTaxTypeName().trim());

        taxType.setDescription(
                request.getDescription() != null
                        ? request.getDescription().trim()
                        : null
        );

        TaxTypeMaster updatedTaxType =
                taxTypeMasterRepository.save(taxType);

        return mapToResponse(updatedTaxType);
    }

    @Override
    @Transactional(readOnly = true)
    public TaxTypeResponseDto getTaxTypeById(UUID taxTypeId) {

        TaxTypeMaster taxType = taxTypeMasterRepository.findById(taxTypeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tax type not found with ID: " + taxTypeId
                ));

        return mapToResponse(taxType);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxTypeResponseDto> getAllTaxTypes() {

        return taxTypeMasterRepository
                .findAllByOrderByTaxTypeNameAsc()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxTypeResponseDto> getActiveTaxTypes() {

        return taxTypeMasterRepository
                .findByIsActiveTrueOrderByTaxTypeNameAsc()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public void deactivateTaxType(UUID taxTypeId) {

        TaxTypeMaster taxType = taxTypeMasterRepository.findById(taxTypeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tax type not found with ID: " + taxTypeId
                ));

        taxType.setIsActive(false);

        taxTypeMasterRepository.save(taxType);
    }

    private TaxTypeResponseDto mapToResponse(TaxTypeMaster taxType) {

        return TaxTypeResponseDto.builder()
                .taxTypeId(taxType.getTaxTypeId())
                .taxTypeCode(taxType.getTaxTypeCode())
                .taxTypeName(taxType.getTaxTypeName())
                .description(taxType.getDescription())
                .isActive(taxType.getIsActive())
                .createdAt(taxType.getCreatedAt())
                .updatedAt(taxType.getUpdatedAt())
                .build();
    }

}
