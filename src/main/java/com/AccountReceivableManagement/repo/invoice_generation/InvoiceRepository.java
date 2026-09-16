package com.AccountReceivableManagement.repo.invoice_generation;

import com.AccountReceivableManagement.entity.invoice_generation.Invoice;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    /**
     * Backs {@code GET /api/v1/invoices/pending-approval}.
     */
    List<Invoice> findAllByStatusOrderByGeneratedAtDesc(InvoiceStatus status);

    /**
     * Backs {@code GET /api/v1/invoices/approval-workspace}. An invoice has
     * "entered the approval workflow" iff it has at least one
     * {@code InvoiceApprovalHistory} row - the source of truth used here,
     * evaluated as a single DB-level EXISTS subquery rather than filtering
     * by status in Java or scanning BillingSnapshot/BillingConfiguration.
     * In practice this currently coincides with
     * {@code status IN (PENDING_APPROVAL, APPROVED, REJECTED)} since that is
     * the only way to reach those statuses, but querying off the history
     * table is what the workspace is meant to reflect.
     */
    @Query(
            "SELECT i FROM Invoice i "
                    + "WHERE EXISTS ("
                    + "  SELECT 1 FROM InvoiceApprovalHistory h WHERE h.invoiceId = i.invoiceId"
                    + ") "
                    + "ORDER BY i.generatedAt DESC"
    )
    List<Invoice> findAllInApprovalWorkflowOrderByGeneratedAtDesc();
}
