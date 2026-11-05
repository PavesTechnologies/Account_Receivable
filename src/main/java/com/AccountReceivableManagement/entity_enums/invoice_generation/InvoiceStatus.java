package com.AccountReceivableManagement.entity_enums.invoice_generation;

/**
 * Invoice business status.
 * <pre>
 * GENERATED ─submit─▶ PENDING_APPROVAL ─approve─▶ APPROVED ─document finalized─▶ READY_TO_SEND
 *                          └─reject─▶ REJECTED ─(correction, resubmit)─▶ PENDING_APPROVAL
 * </pre>
 * Approval and document finalization are separate: APPROVED is an approved
 * invoice whose document has not (yet) been finalized - e.g. generation
 * failed, or it was approved before finalization existed. READY_TO_SEND is
 * an approved invoice whose PDF is stored ({@code InvoiceDocument}) and can
 * be sent. Sending stays a manual action and is recorded on
 * {@code InvoiceDelivery}, never here: READY_TO_SEND does not mean sent.
 */
public enum InvoiceStatus {
    GENERATED,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    READY_TO_SEND
}
