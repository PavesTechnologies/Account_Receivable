package com.AccountReceivableManagement.service_Imple.invoice_software_selection;

import com.AccountReceivableManagement.dto.invoice_software_selection.InvoiceSoftwareSelectionResponseDto;
import com.AccountReceivableManagement.dto.rms_assets.ProjectBillableAssetResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.CurrencyMaster;
import com.AccountReceivableManagement.entity.tool_catalog.ToolCatalog;
import com.AccountReceivableManagement.integration.rms_assets.RmsAssetIntegrationService;
import com.AccountReceivableManagement.repo.tool_catalog.ToolCatalogRepository;
import com.AccountReceivableManagement.service_interface.invoice_software_selection.InvoiceSoftwareSelectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceSoftwareSelectionServiceImpl implements InvoiceSoftwareSelectionService {

    private static final String NO_PRICING_REASON = "No pricing configured.";

    private final RmsAssetIntegrationService rmsAssetIntegrationService;
    private final ToolCatalogRepository toolCatalogRepository;

    @Override
    public List<InvoiceSoftwareSelectionResponseDto> getSelectableAssets(Long projectId) {

        return rmsAssetIntegrationService.getBillableAssetsForProject(projectId)
                .stream()
                .filter(asset -> Boolean.TRUE.equals(asset.getBillableEligible()))
                .map(this::toSelectionResponse)
                .sorted(Comparator
                        .comparing((InvoiceSoftwareSelectionResponseDto dto) -> !Boolean.TRUE.equals(dto.getSelectionEligible()))
                        .thenComparing(InvoiceSoftwareSelectionResponseDto::getAssetName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private InvoiceSoftwareSelectionResponseDto toSelectionResponse(ProjectBillableAssetResponseDto asset) {

        Optional<ToolCatalog> pricing = toolCatalogRepository.findByAssetId(asset.getAssetId());

        InvoiceSoftwareSelectionResponseDto.InvoiceSoftwareSelectionResponseDtoBuilder builder =
                InvoiceSoftwareSelectionResponseDto.builder()
                        .assetId(asset.getAssetId())
                        .assetCode(asset.getAssetCode())
                        .assetName(asset.getAssetName())
                        .assetCategory(asset.getAssetCategory())
                        .quantity(asset.getQuantity())
                        .billingBasis(asset.getBillingBasis())
                        .assignmentStartDate(asset.getAssignmentStartDate())
                        .assignmentEndDate(asset.getAssignmentEndDate());

        if (pricing.isPresent()) {

            ToolCatalog toolCatalog = pricing.get();
            CurrencyMaster currency = toolCatalog.getCurrency();

            builder.unitPrice(toolCatalog.getUnitPrice())
                    .currencyId(currency.getCurrencyId())
                    .currencyCode(currency.getCurrencyCode())
                    .currencyName(currency.getCurrencyName())
                    .description(toolCatalog.getDescription())
                    .selectionEligible(true)
                    .selectionReason(null);
        } else {

            builder.selectionEligible(false)
                    .selectionReason(NO_PRICING_REASON);
        }

        return builder.build();
    }
}
