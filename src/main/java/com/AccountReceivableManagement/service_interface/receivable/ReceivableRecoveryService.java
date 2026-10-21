package com.AccountReceivableManagement.service_interface.receivable;

import com.AccountReceivableManagement.dto.recovery.RecoveryResultDto;

import java.util.UUID;

/**
 * G-21: every invoice with a SENT delivery gets exactly one receivable, even
 * when creating it failed after the email went out. Both entry points reuse
 * {@link ReceivableService#createReceivableFromInvoice} - idempotent, locked
 * on the invoice, guarded by uk_receivable_invoice - and never send email.
 */
public interface ReceivableRecoveryService {

    /** Operator (OPERATIONS_RECOVERY): create the missing receivable of one sent invoice. */
    RecoveryResultDto recoverReceivable(UUID invoiceId, String reason);

    /**
     * System: creates the receivables of up to {@code limit} sent invoices
     * that have none. A failure on one invoice is logged and counted and
     * does not stop the others; the next run tries it again.
     *
     * @return how many receivables were created
     */
    int reconcileSentInvoicesWithoutReceivable(int limit);
}
