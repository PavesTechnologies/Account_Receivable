package com.AccountReceivableManagement.repo.invoice_generation;

import com.AccountReceivableManagement.entity.invoice_generation.InvoiceDelivery;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceDeliveryRepository extends JpaRepository<InvoiceDelivery, UUID> {

    /**
     * Every delivery attempt for one invoice, newest first - backs
     * {@code GET /api/v1/invoices/{invoiceId}/deliveries}. The first entry
     * is the current delivery state.
     */
    List<InvoiceDelivery> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

    /**
     * Whether any delivery attempt for the invoice reached the given status -
     * with {@code SENT}, whether the invoice has ever been dispatched.
     */
    boolean existsByInvoiceIdAndStatus(UUID invoiceId, InvoiceDeliveryStatus status);

    /** Whether any delivery of the invoice is in one of {@code statuses}. */
    boolean existsByInvoiceIdAndStatusIn(UUID invoiceId, java.util.Collection<InvoiceDeliveryStatus> statuses);

    /**
     * Invoices with a SENT delivery but no receivable (G-21), bounded -
     * the reconciliation job's work list.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT d.invoiceId FROM InvoiceDelivery d WHERE d.status = :sent "
                    + "AND NOT EXISTS (SELECT rc.receivableId FROM Receivable rc WHERE rc.invoice.invoiceId = d.invoiceId) "
                    + "ORDER BY d.invoiceId")
    List<UUID> findSentInvoiceIdsWithoutReceivable(
            @org.springframework.data.repository.query.Param("sent") InvoiceDeliveryStatus sent,
            org.springframework.data.domain.Pageable page);
}
