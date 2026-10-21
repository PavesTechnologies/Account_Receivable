package com.AccountReceivableManagement.entity.invoice_generation;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The finalized PDF of an approved invoice - what is reviewed, downloaded
 * and sent to the client. Stored in the AR database itself, so it is written
 * in the same transaction that moves the invoice to READY_TO_SEND: an
 * invoice is READY_TO_SEND exactly when its document row exists.
 * {@code invoiceId} is a plain reference, as on {@link InvoiceDelivery} and
 * {@code InvoiceApprovalHistory}; {@code uk_invoice_document_invoice} keeps
 * it to one document per invoice.
 */
@Entity
@Table(
        name = "invoice_document",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_invoice_document_invoice",
                columnNames = {"invoice_id"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "invoice_document_id")
    private UUID invoiceDocumentId;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    /** Download file name, e.g. {@code invoice-INV-2026-0001.pdf}. */
    @Column(name = "file_name", nullable = false, length = 150)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "content", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] content;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    /** SHA-256 of {@link #content}, hex - lets a sent document be matched to the stored one. */
    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;
}
