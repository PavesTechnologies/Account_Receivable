package com.AccountReceivableManagement.repo.invoice_generation;

import com.AccountReceivableManagement.entity.invoice_generation.Invoice;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice>
    findByBillingSnapshotId(UUID billingSnapshotId);

    boolean existsByBillingSnapshotId(UUID billingSnapshotId);

    Optional<Invoice> findByBillingScheduleId(UUID billingScheduleId);

    boolean existsByBillingScheduleId(UUID billingScheduleId);

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

    /**
     * Row-level {@code SELECT ... FOR UPDATE} lock, held for the rest of the
     * caller's transaction - no schema change. Used by financial-correction
     * (Phase 2B reacquisition) so two concurrent correction requests for the
     * same invoice serialize instead of racing to rebuild the same
     * BillingSnapshot; the second request blocks here until the first
     * commits, then correctly observes {@code correctionRequired == false}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.invoiceId = :invoiceId")
    Optional<Invoice> findByIdForUpdate(@Param("invoiceId") UUID invoiceId);
}
