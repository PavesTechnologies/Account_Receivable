package com.AccountReceivableManagement.mapper.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotResponseDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import org.springframework.stereotype.Component;

/**
 * Maps a persisted {@link BillingSnapshot} to the API response contract.
 * Field copying only — no business logic.
 */
@Component
public class BillingSnapshotMapper {

    public BillingSnapshotResponseDto toResponse(BillingSnapshot snapshot) {
        return BillingSnapshotResponseDto.builder()
                .snapshotId(snapshot.getId())
                .snapshotNumber(snapshot.getSnapshotNumber())
                .projectId(snapshot.getProjectId())
                .clientId(snapshot.getClientId())
                .billingType(snapshot.getBillingType())
                .currencyCode(snapshot.getCurrencyCode())
                .paymentTermCode(snapshot.getPaymentTermCode())
                .billingFrequency(snapshot.getBillingFrequency())
                .taxRegionCode(snapshot.getTaxRegionCode())
                .billingPeriodStart(snapshot.getBillingPeriodStart())
                .billingPeriodEnd(snapshot.getBillingPeriodEnd())
                .subtotal(snapshot.getSubtotal())
                .expenseAmount(snapshot.getExpenseAmount())
                .totalAmount(snapshot.getTotalAmount())
                .status(snapshot.getStatus())
                .build();
    }
}
