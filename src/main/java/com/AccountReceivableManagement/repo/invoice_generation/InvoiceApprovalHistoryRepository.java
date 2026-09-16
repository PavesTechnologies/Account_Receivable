package com.AccountReceivableManagement.repo.invoice_generation;

import com.AccountReceivableManagement.entity.invoice_generation.InvoiceApprovalHistory;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceApprovalAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceApprovalHistoryRepository extends JpaRepository<InvoiceApprovalHistory, UUID> {

    List<InvoiceApprovalHistory>
    findByInvoiceIdOrderByActionAtAsc(UUID invoiceId);

    Optional<InvoiceApprovalHistory>
    findTopByInvoiceIdAndActionOrderByActionAtDesc(
            UUID invoiceId,
            InvoiceApprovalAction action
    );

    /**
     * Bulk fetch for {@code GET /api/v1/invoices/approval-workspace} - one
     * query for every returned invoice's full history, instead of a
     * per-invoice lookup.
     */
    List<InvoiceApprovalHistory>
    findByInvoiceIdInOrderByActionAtAsc(Collection<UUID> invoiceIds);
}
