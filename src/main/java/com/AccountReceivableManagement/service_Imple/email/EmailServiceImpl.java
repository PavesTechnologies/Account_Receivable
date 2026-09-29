package com.AccountReceivableManagement.service_Imple.email;

import com.AccountReceivableManagement.service_interface.email.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around Spring's {@link JavaMailSender}, configured for Gmail
 * SMTP via {@code spring.mail.*} properties (host/port/credentials all come
 * from environment variables - see application.properties and
 * .env.example). Never logs the message body, recipient list beyond what
 * SLF4J already redacts by convention, or any credential value.
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    private final String fromAddress;

    public EmailServiceImpl(
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public String sendEmailWithAttachment(
            String to,
            String subject,
            String body,
            byte[] attachmentBytes,
            String attachmentFilename,
            String attachmentContentType
    ) {

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(mimeMessage, true);

            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);

            if (attachmentBytes != null && attachmentBytes.length > 0) {
                helper.addAttachment(
                        attachmentFilename,
                        new ByteArrayResource(attachmentBytes),
                        attachmentContentType
                );
            }

            // Explicit saveChanges() guarantees a Message-ID is generated
            // before we read it, independent of whether the send()
            // implementation itself performs this step.
            mimeMessage.saveChanges();

            mailSender.send(mimeMessage);

            return mimeMessage.getMessageID();

        } catch (MessagingException | MailException ex) {

            log.error(
                    "Invoice email delivery failed for recipient domain [{}]: {}",
                    maskRecipient(to),
                    ex.getMessage()
            );

            throw new EmailDeliveryException(
                    "Failed to send invoice email through the configured mail provider.",
                    ex
            );
        }
    }

    /**
     * Logs only the recipient's domain, never the full address, so
     * application logs never accumulate a plaintext record of client email
     * addresses.
     */
    private String maskRecipient(String to) {
        if (to == null) {
            return "unknown";
        }
        int at = to.indexOf('@');
        return at >= 0 ? "***" + to.substring(at) : "***";
    }
}
