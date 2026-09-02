package com.AccountReceivableManagement.builder.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.TimesheetDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshotItem;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps a {@link BillingSnapshotBuilderContext} into a {@link BillingSnapshot}
 * with its {@link BillingSnapshotItem}s attached. Pure mapping only — every
 * business decision (totals, status, snapshot number) is made by the Service
 * and supplied through the context; the Builder only copies and wires.
 */
@Component
public class BillingSnapshotBuilder {

    public BillingSnapshot build(BillingSnapshotBuilderContext context) {
        List<TimesheetDto> timesheets = context.getAcquisitionResult().getTimesheets();

        List<BillingSnapshotItem> items = new ArrayList<>();
        for (TimesheetDto timesheet : timesheets) {
            items.add(BillingSnapshotItem.builder()
                    .itemType(BillingItemType.TIME_ENTRY)
                    .itemName(timesheet.getResourceName())
                    .sourceReferenceId(timesheet.getSourceReferenceId())
                    .quantity(timesheet.getHours())
                    .rate(timesheet.getHourlyRate())
                    .amount(timesheet.getHours().multiply(timesheet.getHourlyRate()))
                    .workDate(timesheet.getWorkDate())
                    .approvalStatus(timesheet.getApprovalStatus())
                    .role(timesheet.getRole())
                    .build());
        }

        BillingSnapshot snapshot = BillingSnapshot.builder()
                .snapshotNumber(context.getSnapshotNumber())
                .billingConfigurationId(context.getConfiguration().getBillingConfigurationId())
                .clientId(context.getClientId())
                .projectId(context.getRequest().getProjectId())
                .billingTypeId(context.getConfiguration().getBillingTypeId())
                .billingType(context.getConfiguration().getBillingTypeName() != null ? context.getConfiguration().getBillingTypeName() : (context.getConfiguration().getBillingType() != null ? context.getConfiguration().getBillingType().name() : null))
                .currencyId(context.getConfiguration().getCurrencyId())
                .currencyCode(context.getConfiguration().getCurrencyCode())
                .paymentTermId(context.getConfiguration().getPaymentTermId())
                .paymentTermCode(context.getConfiguration().getPaymentTermCode() != null ? context.getConfiguration().getPaymentTermCode() : context.getConfiguration().getPaymentTermName())
                .billingFrequencyId(context.getConfiguration().getBillingFrequencyId())
                .billingFrequency(context.getConfiguration().getBillingFrequencyName())
                .taxRegionId(context.getConfiguration().getTaxRegionId())
                .taxRegionCode(context.getConfiguration().getTaxRegionCode())
                .sourceTaxJurisdictionCode(context.getSourceTaxJurisdictionCode())
                .destinationTaxJurisdictionCode(context.getDestinationTaxJurisdictionCode())
                .billingPeriodStart(context.getRequest().getBillingPeriodStart())
                .billingPeriodEnd(context.getRequest().getBillingPeriodEnd())
                .status(context.getStatus())
                .subtotal(context.getSubtotal())
                .expenseAmount(context.getExpenseAmount())
                .totalAmount(context.getTotalAmount())
                .items(items)
                .createdBy(context.getCreatedBy())
                .createdDate(java.time.LocalDateTime.now())
                .updatedDate(java.time.LocalDateTime.now())
                .build();

        items.forEach(item -> item.setBillingSnapshot(snapshot));

        return snapshot;
    }
}
