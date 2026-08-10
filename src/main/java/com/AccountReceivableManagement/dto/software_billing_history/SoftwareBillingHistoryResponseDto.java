package com.AccountReceivableManagement.dto.software_billing_history;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoftwareBillingHistoryResponseDto {

    private String invoiceNumber;

    private LocalDate billingPeriodStart;

    private LocalDate billingPeriodEnd;

    private BigDecimal quantity;

    private BigDecimal amount;

    private String currencyCode;

    private LocalDateTime billedAt;
}
