package com.AccountReceivableManagement.entity.invoice_generation;

import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit record of one "Send to Client" delivery attempt. {@code invoiceId}
 * is a plain, unvalidated reference rather than a JPA relationship,
 * matching the convention already used by {@code InvoiceApprovalHistory}:
 * this table only records delivery history, it does not own or cascade
 * with {@link Invoice}, and every attempt inserts a new row rather than
 * overwriting the previous one. The latest row for a given {@code invoiceId}
 * is the current delivery state. An invoice has at most one ordinary
 * delivery: a new attempt is allowed only while every earlier one FAILED
 * (G-19) - enforced under the invoice's row lock by the delivery service.
 * {@link Invoice#getStatus()} is never modified by a delivery attempt.
 */
@Entity
@Table(
        name = "invoice_delivery",
        indexes = {
                @Index(
                        name = "idx_invoice_delivery_invoice",
                        columnList = "invoice_id"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "invoice_delivery_id")
    private UUID invoiceDeliveryId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    /*
     * columnDefinition forced to VARCHAR for the same reason as
     * Invoice.status/InvoiceApprovalHistory's enum columns: Hibernate 6's
     * MySQLDialect otherwise maps @Enumerated(STRING) to a native MySQL
     * ENUM(...) frozen to whatever constants exist at table-creation time.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private InvoiceDeliveryStatus status;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * The JavaMail-generated {@code Message-ID} of the sent message
     * ({@code MimeMessage.getMessageID()}) - a genuine artifact of the
     * message that was actually transmitted, not a fabricated value.
     * {@code null} unless the attempt reached {@code SENT}.
     */
    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    /** The authenticated user who requested this delivery (G-19 accountability). */
    @Column(name = "requested_by", length = 100)
    private String requestedBy;

    @Column(name = "requested_by_user_id")
    private Long requestedByUserId;

    /**
     * Set when an operator resolved a PENDING/UNCERTAIN outcome to SENT or
     * FAILED after checking the mailbox - who, when and why.
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolved_by_user_id")
    private Long resolvedByUserId;

    @Column(name = "resolution_reason", length = 500)
    private String resolutionReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if (retryCount == null) {
            retryCount = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
