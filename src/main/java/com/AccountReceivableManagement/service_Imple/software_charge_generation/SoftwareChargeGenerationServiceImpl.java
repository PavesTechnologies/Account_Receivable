package com.AccountReceivableManagement.service_Imple.software_charge_generation;

import com.AccountReceivableManagement.dto.invoice_software_selection.InvoiceSoftwareSelectionResponseDto;
import com.AccountReceivableManagement.dto.software_charge_generation.SoftwareChargeLineDto;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.service_interface.software_charge_generation.SoftwareChargeGenerationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SoftwareChargeGenerationServiceImpl implements SoftwareChargeGenerationService {

    @Override
    public List<SoftwareChargeLineDto> generateChargeLines(List<InvoiceSoftwareSelectionResponseDto> selectedAssets) {

        return selectedAssets.stream()
                .map(this::toChargeLine)
                .collect(Collectors.toList());
    }

    private SoftwareChargeLineDto toChargeLine(InvoiceSoftwareSelectionResponseDto selectedAsset) {

        if (!Boolean.TRUE.equals(selectedAsset.getSelectionEligible())) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Asset '" + selectedAsset.getAssetName() + "' is not eligible for charge generation.");
        }

        BigDecimal calculatedAmount = new BigDecimal(selectedAsset.getQuantity())
                .multiply(selectedAsset.getUnitPrice());

        return SoftwareChargeLineDto.builder()
                .assetId(selectedAsset.getAssetId())
                .assetCode(selectedAsset.getAssetCode())
                .assetName(selectedAsset.getAssetName())
                .billingBasis(selectedAsset.getBillingBasis())
                .quantity(selectedAsset.getQuantity())
                .unitPrice(selectedAsset.getUnitPrice())
                .currencyId(selectedAsset.getCurrencyId())
                .currencyCode(selectedAsset.getCurrencyCode())
                .currencyName(selectedAsset.getCurrencyName())
                .assignmentStartDate(selectedAsset.getAssignmentStartDate())
                .assignmentEndDate(selectedAsset.getAssignmentEndDate())
                .description(buildDescription(selectedAsset))
                .calculatedAmount(calculatedAmount)
                .build();
    }

    private String buildDescription(InvoiceSoftwareSelectionResponseDto selectedAsset) {

        String period = formatAssignmentPeriod(
                selectedAsset.getAssignmentStartDate(), selectedAsset.getAssignmentEndDate());

        return selectedAsset.getAssetName()
                + " - " + selectedAsset.getBillingBasis()
                + " - " + period
                + " - Qty: " + selectedAsset.getQuantity();
    }

    private String formatAssignmentPeriod(LocalDate startDate, LocalDate endDate) {

        String start = startDate != null ? startDate.toString() : "N/A";
        String end = endDate != null ? endDate.toString() : "Ongoing";

        return start + " to " + end;
    }
}
