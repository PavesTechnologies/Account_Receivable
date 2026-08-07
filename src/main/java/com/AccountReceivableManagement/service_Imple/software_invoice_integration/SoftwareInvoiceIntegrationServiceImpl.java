package com.AccountReceivableManagement.service_Imple.software_invoice_integration;

import com.AccountReceivableManagement.dto.software_charge_generation.SoftwareChargeLineDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshotItem;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.service_interface.software_invoice_integration.SoftwareInvoiceIntegrationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SoftwareInvoiceIntegrationServiceImpl implements SoftwareInvoiceIntegrationService {

    @Override
    public List<BillingSnapshotItem> toInvoiceLines(List<SoftwareChargeLineDto> softwareCharges) {

        return softwareCharges.stream()
                .map(this::toInvoiceLine)
                .collect(Collectors.toList());
    }

    private BillingSnapshotItem toInvoiceLine(SoftwareChargeLineDto charge) {

        return BillingSnapshotItem.builder()
                .itemType(BillingItemType.SOFTWARE)
                .itemName(charge.getDescription())
                .sourceReferenceId(charge.getAssetId().toString())
                .quantity(BigDecimal.valueOf(charge.getQuantity()))
                .rate(charge.getUnitPrice())
                .amount(charge.getCalculatedAmount())
                .build();
    }
}
