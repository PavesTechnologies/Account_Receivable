package com.AccountReceivableManagement.dto.invoice_generation;

import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the {@code GET /api/v1/invoices} listing - the Invoice
 * Generation workspace table. Deliberately excludes {@code items} and
 * {@code taxComponents}; use {@code GET /api/v1/billing-snapshots/{snapshotId}/invoice}
 * for the full invoice detail.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceSummaryResponseDto {

    private UUID invoiceId;

    private String invoiceNumber;

    private InvoiceStatus status;

    private UUID billingSnapshotId;

    private String billingSnapshotNumber;

    private String clientName;

    private String projectName;

    private LocalDate billingPeriodStart;

    private LocalDate billingPeriodEnd;

    private LocalDate invoiceDate;

    private LocalDate dueDate;

    private String currencyCode;

    private String paymentTermCode;

    private BigDecimal subtotal;

    private BigDecimal totalTaxAmount;

    private BigDecimal grandTotal;
}
