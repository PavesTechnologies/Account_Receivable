package com.AccountReceivableManagement.demo;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * A {@link org.springframework.mail.javamail.JavaMailSender} that can never deliver anything: it opens no SMTP connection,
 * needs no host, and only logs that a message was suppressed (a count, never recipients, subject or body). Used by the
 * {@code local-demo} profile so real email delivery is impossible by construction, not merely by configuration.
 */
@Slf4j
public class LogOnlyMailSender extends JavaMailSenderImpl {

    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) throws MailException {
        log.warn("[local-demo] Email NOT sent: log-only sender, no SMTP connection is made ({} message(s) suppressed).",
                mimeMessages == null ? 0 : mimeMessages.length);
    }

    @Override
    public void testConnection() {
        // Nothing to connect to.
    }
}
