package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.TaxStructureResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxComponentMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRegimeMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRegionMaster;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxComponentMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRegimeMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRegionMasterRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.TaxStructureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaxStructureServiceImpl implements TaxStructureService {

    private final TaxRegionMasterRepository taxRegionRepository;
    private final TaxRegimeMasterRepository taxRegimeRepository;
    private final TaxComponentMasterRepository taxComponentRepository;

    @Override
    public TaxStructureResponseDto getTaxStructureByRegion(UUID taxRegionId) {

        TaxRegionMaster region = taxRegionRepository.findById(taxRegionId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler
                                .ResourceNotFoundException(
                                "Tax region not found."
                        )
                );

        TaxStructureResponseDto.TaxRegionInfo regionInfo =
                TaxStructureResponseDto.TaxRegionInfo.builder()
                        .taxRegionId(region.getTaxRegionId())
                        .taxRegionCode(region.getTaxRegionCode())
                        .taxRegionName(region.getTaxRegionName())
                        .currencyCode(region.getCurrencyCode())
                        .build();

        List<TaxStructureResponseDto.TaxRegimeStructure> regimes =
                getActiveRegimesWithComponents(LocalDate.now());

        return TaxStructureResponseDto.builder()
                .taxRegion(regionInfo)
                .taxRegimes(regimes)
                .build();
    }

    private List<TaxStructureResponseDto.TaxRegimeStructure> getActiveRegimesWithComponents(
            LocalDate onDate
    ) {

        return taxRegimeRepository.findByIsActiveTrueOrderByTaxRegimeNameAsc()
                .stream()
                .map(regime -> mapToRegimeStructure(regime, onDate))
                .collect(Collectors.toList());
    }

    private TaxStructureResponseDto.TaxRegimeStructure mapToRegimeStructure(
            TaxRegimeMaster regime,
            LocalDate onDate
    ) {

        List<TaxStructureResponseDto.TaxComponentInfo> components =
                taxComponentRepository.findActiveComponentsByRegime(
                        regime.getTaxRegimeId(),
                        onDate
                )
                .stream()
                .map(this::mapToComponentInfo)
                .collect(Collectors.toList());

        return TaxStructureResponseDto.TaxRegimeStructure.builder()
                .taxRegimeId(regime.getTaxRegimeId())
                .taxRegimeCode(regime.getTaxRegimeCode())
                .taxRegimeName(regime.getTaxRegimeName())
                .description(regime.getDescription())
                .components(components)
                .build();
    }

    private TaxStructureResponseDto.TaxComponentInfo mapToComponentInfo(
            TaxComponentMaster component
    ) {

        return TaxStructureResponseDto.TaxComponentInfo.builder()
                .taxComponentId(component.getTaxComponentId())
                .taxTypeId(component.getTaxType().getTaxTypeId())
                .taxTypeCode(component.getTaxType().getTaxTypeCode())
                .taxTypeName(component.getTaxType().getTaxTypeName())
                .componentCode(component.getComponentCode())
                .componentName(component.getComponentName())
                .description(component.getDescription())
                .inputType(component.getInputType())
                .displayOrder(component.getDisplayOrder())
                .build();
    }
}
