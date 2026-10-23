package com.AccountReceivableManagement.demo;

import com.AccountReceivableManagement.entity.invoice_generation.InvoiceDelivery;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus;
import com.AccountReceivableManagement.service_Imple.email.EmailServiceImpl;
import com.AccountReceivableManagement.service_interface.email.EmailService;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Demo email delivery semantics: under {@code local-demo} the email service delivers nothing and says so (SIMULATED),
 * while real delivery keeps its meaning (SENT). Offline; the only socket is a loopback listener that must stay unused.
 */
class LocalDemoEmailDeliveryTest {

    private static final EmailService REAL_STAND_IN = new EmailService() {
        @Override
        public String sendEmailWithAttachment(String to, String subject, String body, byte[] bytes, String name, String type) {
            return "<real@provider>";
        }
    };

    private ApplicationContextRunner runner(String... profiles) {
        return new ApplicationContextRunner()
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profiles))
                .withBean("realEmailService", EmailService.class, () -> REAL_STAND_IN)
                .withUserConfiguration(DemoEmailService.class);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Which implementation is used
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    void underLocalDemoTheDemoServiceWinsOverAnyOtherEmailService() {
        runner("local-demo").run(ctx -> {
            assertThat(ctx).hasSingleBean(DemoEmailService.class);
            EmailService chosen = ctx.getBean(EmailService.class); // @Primary resolves the ambiguity
            assertThat(chosen).isInstanceOf(DemoEmailService.class);
            assertThat(chosen.isSimulated()).isTrue();
        });
    }

    @Test
    void outsideLocalDemoTheDemoServiceDoesNotExist() {
        for (String[] profiles : new String[][]{{}, {"local"}, {"dev"}, {"prod"}, {"test"}}) {
            runner(profiles).run(ctx -> {
                assertThat(ctx).doesNotHaveBean(DemoEmailService.class);
                assertThat(ctx.getBean(EmailService.class).isSimulated()).isFalse();
            });
        }
    }

    @Test
    void realEmailServicesStayNonSimulated() {
        assertThat(REAL_STAND_IN.isSimulated()).as("default method").isFalse();
        assertThat(new EmailServiceImpl(mock(JavaMailSender.class), "from@example.invalid").isSimulated()).isFalse();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Never claims or performs a send
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    void demoServiceReturnsAMarkerThatCannotBeARealProviderMessageId() {
        DemoEmailService service = new DemoEmailService();

        String id = service.sendEmailWithAttachment("client@example.invalid", "subject", "body", new byte[]{1}, "a.pdf",
                "application/pdf");
        String other = service.sendEmailWithAttachment("client@example.invalid", "subject", "body", null, null, null);

        assertThat(id).startsWith(DemoEmailService.MESSAGE_ID_PREFIX).doesNotContain("@", "<", ">");
        assertThat(other).isNotEqualTo(id);
    }

    @Test
    void neitherDemoMailPathOpensAnSmtpConnection() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            listener.setSoTimeout(1500);
            // Even pointed at a live listener, the demo paths must never connect to it.
            LogOnlyMailSender sender = new LogOnlyMailSender();
            sender.setHost("127.0.0.1");
            sender.setPort(listener.getLocalPort());
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo("client@example.invalid");
            message.setText("body");
            sender.send(message);
            sender.send(sender.createMimeMessage());
            new DemoEmailService().sendEmailWithAttachment("client@example.invalid", "s", "b", new byte[]{1}, "a.pdf", "application/pdf");

            boolean connected;
            try {
                listener.accept().close();
                connected = true;
            } catch (SocketTimeoutException expected) {
                connected = false;
            }
            assertThat(connected).as("an SMTP connection was attempted").isFalse();
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Status model
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    void simulatedIsADistinctStatusThatFitsTheExistingColumn() throws Exception {
        assertThat(InvoiceDeliveryStatus.valueOf("SIMULATED")).isNotEqualTo(InvoiceDeliveryStatus.SENT);
        // existing constants are untouched and SIMULATED was appended, so no stored value changes meaning
        assertThat(InvoiceDeliveryStatus.values()).extracting(Enum::name)
                .containsExactly("PENDING", "SENT", "FAILED", "SIMULATED");
        Column column = InvoiceDelivery.class.getDeclaredField("status").getAnnotation(Column.class);
        assertThat(column.columnDefinition()).isEqualTo("VARCHAR(20)");
        assertThat("SIMULATED".length()).isLessThanOrEqualTo(column.length());
    }
}
