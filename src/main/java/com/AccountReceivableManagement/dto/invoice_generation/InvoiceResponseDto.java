package com.AccountReceivableManagement.dto.invoice_generation;

import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceResponseDto {

    private UUID invoiceId;

    private String invoiceNumber;

    private InvoiceStatus status;

    private UUID billingSnapshotId;

    private String billingSnapshotNumber;

    private UUID taxCalculationId;

    private UUID clientId;

    private String clientName;

    private String billingAddress;

    private String gstinOrTaxId;

    private String contact;

    private Long projectId;

    private String projectName;

    private LocalDate billingPeriodStart;

    private LocalDate billingPeriodEnd;

    private String currencyCode;

    private String paymentTermCode;

    private LocalDate invoiceDate;

    private LocalDate dueDate;

    private List<InvoiceItemResponseDto> items;

    private List<InvoiceTaxComponentResponseDto> taxComponents;

    private BigDecimal subtotal;

    private BigDecimal totalTaxAmount;

    private BigDecimal grandTotal;

    private LocalDateTime generatedAt;

    /**
     * {@code true} only when {@code status == REJECTED} and no correction
     * refresh has occurred since the latest rejection - i.e. the invoice is
     * not yet safe to resubmit. Always {@code false} for every other
     * status. Derived from {@code InvoiceApprovalHistory}, never stored.
     */
    private boolean correctionRequired;

    /**
     * Timestamp of the most recent {@code CORRECTED} approval-history entry
     * for this invoice, or {@code null} if it has never been refreshed
     * after a rejection. Derived from {@code InvoiceApprovalHistory}, never
     * stored on the invoice itself.
     */
    private LocalDateTime lastCorrectedAt;
}
