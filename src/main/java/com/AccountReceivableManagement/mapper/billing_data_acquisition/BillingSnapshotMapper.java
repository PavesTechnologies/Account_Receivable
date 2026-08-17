package com.AccountReceivableManagement.mapper.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotResponseDto.TimesheetLineItemDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshotItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps a persisted {@link BillingSnapshot} to the API response contract.
 * The snapshot only stores master-table ids, not names, so the Billing
 * Configuration read alongside it during acquisition supplies the display
 * names. A future read path that reconstructs a snapshot without a
 * freshly-fetched configuration (e.g. {@code GET /billing-snapshots/{id}})
 * will need to resolve those names from Epic 1's master data by id instead.
 *
 * Also maps the persisted {@link BillingSnapshotItem} lines into
 * {@link TimesheetLineItemDto}s so the frontend can render the Labor Charges
 * Preview table immediately from the creation response.
 */
@Component
public class BillingSnapshotMapper {

    public BillingSnapshotResponseDto toResponse(BillingSnapshot snapshot, BillingConfigurationResponseDto configuration) {

        List<TimesheetLineItemDto> timesheetLineItems = snapshot.getItems().stream()
                .map(this::toTimesheetLineItemDto)
                .collect(Collectors.toList());

        return BillingSnapshotResponseDto.builder()
                .snapshotId(snapshot.getId())
                .acquisitionStatus("READY")
                .snapshotNumber(snapshot.getSnapshotNumber())
                .projectId(snapshot.getProjectId())
                .clientId(snapshot.getClientId())
                .billingTypeId(snapshot.getBillingTypeId())
                .billingTypeName(configuration.getBillingTypeName())
                .currencyId(snapshot.getCurrencyId())
                .currencyCode(configuration.getCurrencyCode())
                .paymentTermId(snapshot.getPaymentTermId())
                .paymentTermName(configuration.getPaymentTermName())
                .billingFrequencyId(snapshot.getBillingFrequencyId())
                .billingFrequencyName(configuration.getBillingFrequencyName())
                .taxRegionId(snapshot.getTaxRegionId())
                .taxRegionCode(configuration.getTaxRegionCode())
                .billingPeriodStart(snapshot.getBillingPeriodStart())
                .billingPeriodEnd(snapshot.getBillingPeriodEnd())
                .subtotal(snapshot.getSubtotal())
                .expenseAmount(snapshot.getExpenseAmount())
                .totalAmount(snapshot.getTotalAmount())
                .status(snapshot.getStatus())
                .timesheets(timesheetLineItems)
                .build();
    }

    private TimesheetLineItemDto toTimesheetLineItemDto(BillingSnapshotItem item) {
        return TimesheetLineItemDto.builder()
                .employee(item.getItemName())               // itemName = resourceName
                .sourceReferenceId(item.getSourceReferenceId())
                .workDate(item.getWorkDate())
                .hours(item.getQuantity())                  // quantity = hours
                .rate(item.getRate())
                .amount(item.getAmount())
                .approvalStatus(item.getApprovalStatus())
                .role(item.getRole())
                .build();
    }
}

