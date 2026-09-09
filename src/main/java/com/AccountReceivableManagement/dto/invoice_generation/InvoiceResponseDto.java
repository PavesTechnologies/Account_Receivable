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
}
