package com.AccountReceivableManagement.dto.invoice_generation;

import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDeliveryResponseDto {

    private UUID deliveryId;

    private UUID invoiceId;

    private String invoiceNumber;

    private String recipientEmail;

    private InvoiceDeliveryStatus deliveryStatus;

    private LocalDateTime sentAt;

    private LocalDateTime failedAt;

    private String failureReason;

    private LocalDateTime createdAt;

    /** Who requested the delivery (null for deliveries before V14). */
    private String requestedBy;

    /** Operator resolution of an uncertain outcome, if any. */
    private LocalDateTime resolvedAt;

    private String resolvedBy;

    private String resolutionReason;
}
