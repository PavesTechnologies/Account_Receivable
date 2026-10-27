package com.AccountReceivableManagement.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One invoice in the dashboard ledger. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardInvoiceRowDto {

    private UUID invoiceId;
    private String invoiceNumber;
    private String clientName;
    private String projectName;
    private String billingType;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private String currencyCode;
    private BigDecimal subtotal;
    private BigDecimal totalTaxAmount;
    private BigDecimal grandTotal;
    /** Stored workflow status: GENERATED, PENDING_APPROVAL, APPROVED or REJECTED. */
    private String invoiceStatus;
    private boolean sent;
    /** Null until the invoice is sent. */
    private BigDecimal outstandingBalance;
    /** Days since invoice date while a balance is open; null otherwise. */
    private Long daysOutstanding;
    /** Days past due while a balance is open; null otherwise. */
    private Long daysOverdue;
    /** OVERDUE, ISSUED, AWAITING_SEND, PENDING_APPROVAL, GENERATED or REJECTED. */
    private String displayStatus;
}
