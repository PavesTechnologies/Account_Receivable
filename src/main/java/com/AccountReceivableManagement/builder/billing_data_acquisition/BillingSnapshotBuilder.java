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
                    .build());
        }

        BillingSnapshot snapshot = BillingSnapshot.builder()
                .snapshotNumber(context.getSnapshotNumber())
                .billingConfigurationId(context.getConfiguration().getBillingConfigurationId())
                .clientId(context.getClientId())
                .projectId(context.getRequest().getProjectId())
                .billingType(context.getConfiguration().getBillingType())
                .currencyCode(context.getConfiguration().getCurrencyCode())
                .paymentTermCode(context.getConfiguration().getPaymentTermCode())
                .billingFrequency(context.getConfiguration().getBillingFrequency())
                .taxRegionCode(context.getConfiguration().getTaxRegionCode())
                .billingPeriodStart(context.getRequest().getBillingPeriodStart())
                .billingPeriodEnd(context.getRequest().getBillingPeriodEnd())
                .status(context.getStatus())
                .subtotal(context.getSubtotal())
                .expenseAmount(context.getExpenseAmount())
                .totalAmount(context.getTotalAmount())
                .items(items)
                .createdBy(context.getCreatedBy())
                .build();

        items.forEach(item -> item.setBillingSnapshot(snapshot));

        return snapshot;
    }
}
