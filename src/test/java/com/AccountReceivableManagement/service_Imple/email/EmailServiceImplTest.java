package com.AccountReceivableManagement.service_Imple.email;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Verifies the email-sending wrapper's success/failure contract using a
 * mocked {@link JavaMailSender} - no actual Gmail/SMTP network call is
 * made, per the "no real network calls in automated tests" requirement.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailServiceImpl(mailSender, "sender@example.com");
    }

    private MimeMessage realMimeMessage() {
        return new MimeMessage(Session.getDefaultInstance(new Properties()));
    }

    @Test
    void sendEmailWithAttachment_mailSenderSucceeds_returnsMessageId() {
        MimeMessage mimeMessage = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        String messageId = emailService.sendEmailWithAttachment(
                "client@example.com",
                "Invoice INV-1",
                "Body text",
                new byte[]{1, 2, 3},
                "invoice-INV-1.pdf",
                "application/pdf"
        );

        assertThat(messageId).isNotBlank();
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendEmailWithAttachment_mailSenderThrows_wrapsInEmailDeliveryException() {
        MimeMessage mimeMessage = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP authentication failed"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailService.sendEmailWithAttachment(
                "client@example.com",
                "Invoice INV-1",
                "Body text",
                new byte[]{1, 2, 3},
                "invoice-INV-1.pdf",
                "application/pdf"
        )).isInstanceOf(EmailDeliveryException.class);
    }

    @Test
    void sendEmailWithAttachment_noAttachment_stillSendsPlainMessage() {
        MimeMessage mimeMessage = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        String messageId = emailService.sendEmailWithAttachment(
                "client@example.com",
                "Invoice INV-1",
                "Body text",
                null,
                null,
                null
        );

        assertThat(messageId).isNotBlank();
        verify(mailSender).send(mimeMessage);
    }
}
