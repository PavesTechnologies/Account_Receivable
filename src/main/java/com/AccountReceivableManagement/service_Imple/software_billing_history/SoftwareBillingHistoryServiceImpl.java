package com.AccountReceivableManagement.service_Imple.software_billing_history;

import com.AccountReceivableManagement.dto.software_billing_history.SoftwareBillingHistoryResponseDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshotItem;
import com.AccountReceivableManagement.entity.projectbilling_config.CurrencyMaster;
import com.AccountReceivableManagement.entity.software_billing_history.SoftwareBillingHistory;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.CurrencyMasterRepository;
import com.AccountReceivableManagement.repo.software_billing_history.SoftwareBillingHistoryRepository;
import com.AccountReceivableManagement.service_interface.software_billing_history.SoftwareBillingHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SoftwareBillingHistoryServiceImpl implements SoftwareBillingHistoryService {

    private final SoftwareBillingHistoryRepository softwareBillingHistoryRepository;
    private final CurrencyMasterRepository currencyMasterRepository;

    @Override
    public void recordHistory(BillingSnapshot snapshot) {

        List<BillingSnapshotItem> softwareItems = snapshot.getItems().stream()
                .filter(item -> item.getItemType() == BillingItemType.SOFTWARE)
                .collect(Collectors.toList());

        if (softwareItems.isEmpty()) {
            return;
        }

        String currencyCode = currencyMasterRepository.findById(snapshot.getCurrencyId())
                .map(CurrencyMaster::getCurrencyCode)
                .orElse(null);

        for (BillingSnapshotItem item : softwareItems) {

            UUID assetId = UUID.fromString(item.getSourceReferenceId());

            if (softwareBillingHistoryRepository.existsByAssetIdAndBillingPeriodStartAndBillingPeriodEnd(
                    assetId, snapshot.getBillingPeriodStart(), snapshot.getBillingPeriodEnd())) {

                throw new GlobalExceptionHandler.ValidationException(
                        "Software billing history already exists for asset " + assetId
                                + " for billing period " + snapshot.getBillingPeriodStart()
                                + " to " + snapshot.getBillingPeriodEnd() + ".");
            }

            softwareBillingHistoryRepository.save(SoftwareBillingHistory.builder()
                    .assetId(assetId)
                    .billingSnapshotId(snapshot.getId())
                    .invoiceNumber(snapshot.getSnapshotNumber())
                    .billingPeriodStart(snapshot.getBillingPeriodStart())
                    .billingPeriodEnd(snapshot.getBillingPeriodEnd())
                    .quantity(item.getQuantity())
                    .amount(item.getAmount())
                    .currencyCode(currencyCode)
                    .build());
        }
    }

    @Override
    public List<SoftwareBillingHistoryResponseDto> getHistoryForAsset(UUID assetId) {

        return softwareBillingHistoryRepository.findByAssetId(assetId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private SoftwareBillingHistoryResponseDto mapToResponse(SoftwareBillingHistory history) {

        return SoftwareBillingHistoryResponseDto.builder()
                .invoiceNumber(history.getInvoiceNumber())
                .billingPeriodStart(history.getBillingPeriodStart())
                .billingPeriodEnd(history.getBillingPeriodEnd())
                .quantity(history.getQuantity())
                .amount(history.getAmount())
                .currencyCode(history.getCurrencyCode())
                .billedAt(history.getBilledAt())
                .build();
    }
}
