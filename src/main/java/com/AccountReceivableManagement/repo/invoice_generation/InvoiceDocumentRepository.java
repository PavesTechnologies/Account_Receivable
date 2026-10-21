package com.AccountReceivableManagement.repo.invoice_generation;

import com.AccountReceivableManagement.entity.invoice_generation.InvoiceDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceDocumentRepository extends JpaRepository<InvoiceDocument, UUID> {

    Optional<InvoiceDocument> findByInvoiceId(UUID invoiceId);

    boolean existsByInvoiceId(UUID invoiceId);
}
