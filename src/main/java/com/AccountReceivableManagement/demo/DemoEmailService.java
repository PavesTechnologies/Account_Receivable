package com.AccountReceivableManagement.demo;

import com.AccountReceivableManagement.service_interface.email.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The {@link EmailService} under {@code local-demo}: it delivers nothing, opens no connection and builds no MIME message.
 * It reports {@link #isSimulated()} so the invoice delivery workflow records SIMULATED instead of SENT. The returned
 * message id is a marker ("demo-simulated-...") that can never be a real provider id. Only a count is logged, never the
 * recipient, subject or body.
 */
@Slf4j
@Service
@Primary
@Profile(LocalDemoIsolationValidator.PROFILE)
public class DemoEmailService implements EmailService {

    public static final String MESSAGE_ID_PREFIX = "demo-simulated-";

    @Override
    public String sendEmailWithAttachment(String to, String subject, String body, byte[] attachmentBytes,
                                          String attachmentFilename, String attachmentContentType) {
        log.warn("[local-demo] Email NOT sent: delivery simulated, no SMTP connection is made (1 message suppressed).");
        return MESSAGE_ID_PREFIX + UUID.randomUUID();
    }

    @Override
    public boolean isSimulated() {
        return true;
    }
}
