package com.AccountReceivableManagement.entity.invoice_generation;

import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceApprovalAction;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit record of one Invoice status transition (submit, approve - later
 * reject). {@code invoiceId} is a plain, unvalidated reference rather than a
 * JPA relationship, matching the convention already used by
 * {@code SoftwareBillingHistory}: this table only records history, it does
 * not own or cascade with {@link Invoice}, and is written independently at
 * each action rather than as part of the Invoice's own save graph.
 */
@Entity
@Table(
        name = "invoice_approval_history",
        indexes = {
                @Index(
                        name = "idx_invoice_approval_history_invoice",
                        columnList = "invoice_id"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceApprovalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "invoice_approval_history_id")
    private UUID invoiceApprovalHistoryId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    /*
     * columnDefinition forced to VARCHAR for the same reason as
     * Invoice.status: Hibernate 6's MySQLDialect otherwise maps
     * @Enumerated(STRING) to a native MySQL ENUM(...) frozen to whatever
     * constants exist at table-creation time, which breaks the instant a
     * new status/action value is added later.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private InvoiceStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private InvoiceStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private InvoiceApprovalAction action;

    @Column(name = "action_by", nullable = false, length = 100)
    private String actionBy;

    @Column(name = "action_at", nullable = false)
    private LocalDateTime actionAt;

    /**
     * Optional for {@code SUBMITTED}/{@code APPROVED}. The future
     * {@code REJECTED} action must enforce a non-blank value at the service
     * layer - this column stays nullable so the entity itself does not need
     * to change when that validation is added.
     */
    @Column(name = "comment", length = 500)
    private String comment;
}
