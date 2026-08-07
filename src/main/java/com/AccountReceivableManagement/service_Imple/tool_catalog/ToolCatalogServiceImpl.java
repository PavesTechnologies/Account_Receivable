package com.AccountReceivableManagement.service_Imple.tool_catalog;

import com.AccountReceivableManagement.dto.asset_lookup.AssetLookupResponseDto;
import com.AccountReceivableManagement.dto.tool_catalog.ToolCatalogRequestDto;
import com.AccountReceivableManagement.dto.tool_catalog.ToolCatalogResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.CurrencyMaster;
import com.AccountReceivableManagement.entity.tool_catalog.ToolCatalog;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.integration.asset_lookup.AssetLookupService;
import com.AccountReceivableManagement.repo.projectbilling_config.CurrencyMasterRepository;
import com.AccountReceivableManagement.repo.tool_catalog.ToolCatalogRepository;
import com.AccountReceivableManagement.service_interface.tool_catalog.ToolCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ToolCatalogServiceImpl implements ToolCatalogService {

    private final ToolCatalogRepository toolCatalogRepository;
    private final CurrencyMasterRepository currencyMasterRepository;
    private final AssetLookupService assetLookupService;

    @Override
    public ToolCatalogResponseDto create(ToolCatalogRequestDto request) {

        if (toolCatalogRepository.existsByAssetId(request.getAssetId())) {
            throw new GlobalExceptionHandler.DuplicateResourceException(
                    "Pricing already exists for this Asset.");
        }

        validateUnitPrice(request.getUnitPrice());
        validateEffectiveDates(request.getEffectiveFrom(), request.getEffectiveTo());

        CurrencyMaster currency = currencyMasterRepository.findById(request.getCurrencyId())
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Currency not found."));

        AssetLookupResponseDto asset = assetLookupService.getAssetById(request.getAssetId());

        ToolCatalog toolCatalog = ToolCatalog.builder()
                .assetId(asset.getAssetId())
                .assetCode(asset.getAssetCode())
                .assetName(asset.getAssetName())
                .description(request.getDescription())
                .billingBasis(request.getBillingBasis())
                .unitPrice(request.getUnitPrice())
                .currency(currency)
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .isActive(request.getActive() != null ? request.getActive() : true)
                .build();

        ToolCatalog saved = toolCatalogRepository.save(toolCatalog);

        return mapToResponse(saved);
    }

    @Override
    public ToolCatalogResponseDto update(UUID toolId, ToolCatalogRequestDto request) {

        ToolCatalog toolCatalog = toolCatalogRepository.findById(toolId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Tool not found."));

        if (!toolCatalog.getAssetId().equals(request.getAssetId())
                && toolCatalogRepository.existsByAssetId(request.getAssetId())) {

            throw new GlobalExceptionHandler.DuplicateResourceException(
                    "Pricing already exists for this Asset.");
        }

        validateUnitPrice(request.getUnitPrice());
        validateEffectiveDates(request.getEffectiveFrom(), request.getEffectiveTo());

        CurrencyMaster currency = currencyMasterRepository.findById(request.getCurrencyId())
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Currency not found."));

        AssetLookupResponseDto asset = assetLookupService.getAssetById(request.getAssetId());

        toolCatalog.setAssetId(asset.getAssetId());
        toolCatalog.setAssetCode(asset.getAssetCode());
        toolCatalog.setAssetName(asset.getAssetName());
        toolCatalog.setDescription(request.getDescription());
        toolCatalog.setBillingBasis(request.getBillingBasis());
        toolCatalog.setUnitPrice(request.getUnitPrice());
        toolCatalog.setCurrency(currency);
        toolCatalog.setEffectiveFrom(request.getEffectiveFrom());
        toolCatalog.setEffectiveTo(request.getEffectiveTo());

        if (request.getActive() != null) {
            toolCatalog.setIsActive(request.getActive());
        }

        ToolCatalog updated = toolCatalogRepository.save(toolCatalog);

        return mapToResponse(updated);
    }

    @Override
    public ToolCatalogResponseDto getById(UUID toolId) {

        ToolCatalog toolCatalog = toolCatalogRepository.findById(toolId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Tool not found."));

        return mapToResponse(toolCatalog);
    }

    @Override
    public List<ToolCatalogResponseDto> getAll() {

        return toolCatalogRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ToolCatalogResponseDto> getActive() {

        return toolCatalogRepository.findByIsActiveTrue()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(UUID toolId) {

        ToolCatalog toolCatalog = toolCatalogRepository.findById(toolId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Tool not found."));

        toolCatalog.setIsActive(false);

        toolCatalogRepository.save(toolCatalog);
    }

    private void validateUnitPrice(BigDecimal unitPrice) {

        if (unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new GlobalExceptionHandler.ValidationException("Unit price cannot be negative.");
        }
    }

    private void validateEffectiveDates(LocalDate effectiveFrom, LocalDate effectiveTo) {

        if (effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Effective To cannot be earlier than Effective From.");
        }
    }

    private ToolCatalogResponseDto mapToResponse(ToolCatalog toolCatalog) {

        CurrencyMaster currency = toolCatalog.getCurrency();

        return ToolCatalogResponseDto.builder()
                .toolId(toolCatalog.getToolId())
                .assetId(toolCatalog.getAssetId())
                .assetCode(toolCatalog.getAssetCode())
                .assetName(toolCatalog.getAssetName())
                .description(toolCatalog.getDescription())
                .billingBasis(toolCatalog.getBillingBasis())
                .unitPrice(toolCatalog.getUnitPrice())
                .currencyId(currency.getCurrencyId())
                .currencyCode(currency.getCurrencyCode())
                .currencyName(currency.getCurrencyName())
                .effectiveFrom(toolCatalog.getEffectiveFrom())
                .effectiveTo(toolCatalog.getEffectiveTo())
                .isActive(toolCatalog.getIsActive())
                .createdAt(toolCatalog.getCreatedAt())
                .updatedAt(toolCatalog.getUpdatedAt())
                .build();
    }
}
