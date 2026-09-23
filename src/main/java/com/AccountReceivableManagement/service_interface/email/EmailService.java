package com.AccountReceivableManagement.service_interface.email;

public interface EmailService {

    /**
     * Sends a plain-text email with one attachment through the configured
     * SMTP provider. Returns the sent message's JavaMail {@code Message-ID}
     * on success. Throws {@link EmailDeliveryException} - never a silent
     * failure - if the message cannot be sent for any reason (SMTP
     * authentication, connection, or transport failure).
     */
    String sendEmailWithAttachment(
            String to,
            String subject,
            String body,
            byte[] attachmentBytes,
            String attachmentFilename,
            String attachmentContentType
    );
}
