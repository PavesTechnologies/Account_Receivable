package com.AccountReceivableManagement.dto.invoice_generation;

import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceWorkspaceStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the Invoice Generation workspace: either an invoice candidate
 * (TAX_COMPLETED snapshot, no invoice yet - {@code invoiceId} is null) or an
 * existing invoice. Values are copied from persisted data; nothing is
 * invented, so unavailable values are null.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceGenerationWorkspaceRowDto {

    private InvoiceWorkspaceStatus workspaceStatus;

    private UUID snapshotId;

    /** Set for candidates that come from a TAX_CALCULATED billing schedule (no snapshot exists for them). */
    private UUID billingScheduleId;

    private String snapshotNumber;

    private BillingSnapshotStatus snapshotStatus;

    private String clientName;

    private String projectName;

    private String projectCode;

    private String billingType;

    private LocalDate billingPeriodStart;

    private LocalDate billingPeriodEnd;

    private String currencyCode;

    /** Pre-tax amount: tax-calculation taxable amount for candidates, invoice subtotal otherwise. */
    private BigDecimal amount;

    private BigDecimal totalTaxAmount;

    private BigDecimal grandTotal;

    private UUID invoiceId;

    private String invoiceNumber;

    private InvoiceStatus invoiceStatus;

    private LocalDate invoiceDate;

    private LocalDate dueDate;
}
