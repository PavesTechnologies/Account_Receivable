package com.AccountReceivableManagement.dto.invoice_generation;

import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceItemResponseDto {

    private UUID invoiceItemId;

    private BillingItemType itemType;

    private String itemName;

    private String sourceReferenceId;

    private BigDecimal quantity;

    private BigDecimal rate;

    private BigDecimal amount;

    private LocalDate workDate;

    private String role;
}
