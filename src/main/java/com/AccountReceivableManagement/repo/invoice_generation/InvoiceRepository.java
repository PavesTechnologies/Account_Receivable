package com.AccountReceivableManagement.repo.invoice_generation;

import com.AccountReceivableManagement.entity.invoice_generation.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice>
    findByBillingSnapshotId(UUID billingSnapshotId);

    boolean existsByBillingSnapshotId(UUID billingSnapshotId);

    Optional<Invoice>
    findByInvoiceNumber(String invoiceNumber);

    /**
     * Backs {@code GET /api/v1/invoices} - most recently generated first.
     */
    List<Invoice> findAllByOrderByGeneratedAtDesc();
}
