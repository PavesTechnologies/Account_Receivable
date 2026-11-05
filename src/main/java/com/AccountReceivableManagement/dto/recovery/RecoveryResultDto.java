package com.AccountReceivableManagement.dto.recovery;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Outcome of one operational recovery action. {@code accepted=false} means
 * nothing was changed and {@code message} says why (ineligible state,
 * already done, or still waiting for a human decision).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryResultDto {

    /** The record acted on (run, invoice or delivery id). */
    private UUID recordId;

    /** e.g. AUTOMATIC_BILLING_RUN, RECEIVABLE, INVOICE_DELIVERY. */
    private String recordType;

    private boolean accepted;

    /** The record's state after the action. */
    private String state;

    private String message;
}
