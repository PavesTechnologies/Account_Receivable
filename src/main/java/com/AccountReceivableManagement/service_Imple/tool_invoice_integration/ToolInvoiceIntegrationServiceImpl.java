package com.AccountReceivableManagement.service_Imple.tool_invoice_integration;

import com.AccountReceivableManagement.dto.tool_charge_acquisition.ToolChargePreviewDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshotItem;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotItemRepository;
import com.AccountReceivableManagement.service_interface.tool_invoice_integration.ToolInvoiceIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ToolInvoiceIntegrationServiceImpl implements ToolInvoiceIntegrationService {

    private static final DateTimeFormatter ASSIGNMENT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private final BillingSnapshotItemRepository billingSnapshotItemRepository;

    @Override
    public List<BillingSnapshotItem> toInvoiceLines(List<ToolChargePreviewDto> toolCharges) {

        Set<UUID> assignmentIdsAlreadyLined = new HashSet<>();
        List<BillingSnapshotItem> lines = new ArrayList<>();

        for (ToolChargePreviewDto charge : toolCharges) {

            if (!assignmentIdsAlreadyLined.add(charge.getAssignmentId())) {
                continue;
            }

            if (billingSnapshotItemRepository
                    .existsBySourceReferenceIdAndBillingSnapshot_BillingPeriodStartAndBillingSnapshot_BillingPeriodEnd(
                            charge.getAssignmentId().toString(),
                            charge.getBillingPeriodStart(),
                            charge.getBillingPeriodEnd())) {
                continue;
            }

            lines.add(BillingSnapshotItem.builder()
                    .itemType(BillingItemType.TOOL_CHARGE)
                    .itemName(buildDescription(charge))
                    .sourceReferenceId(charge.getAssignmentId().toString())
                    .quantity(BigDecimal.valueOf(charge.getQuantity()))
                    .rate(charge.getUnitPrice())
                    .amount(charge.getCalculatedAmount())
                    .build());
        }

        return lines;
    }

    private String buildDescription(ToolChargePreviewDto charge) {

        String assignmentPeriod = charge.getAssignmentEndDate() != null
                ? charge.getAssignmentStartDate().format(ASSIGNMENT_DATE_FORMATTER)
                        + " to " + charge.getAssignmentEndDate().format(ASSIGNMENT_DATE_FORMATTER)
                : charge.getAssignmentStartDate().format(ASSIGNMENT_DATE_FORMATTER) + " onwards";

        return charge.getAssetName()
                + "\n\nBilling Basis: " + charge.getBillingBasis()
                + "\n\nQuantity: " + charge.getQuantity()
                + "\n\nAssignment Period:\n" + assignmentPeriod;
    }
}
