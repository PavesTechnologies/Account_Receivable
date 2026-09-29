package com.AccountReceivableManagement.repo.invoice_generation;

import com.AccountReceivableManagement.entity.invoice_generation.InvoiceDelivery;
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
}
