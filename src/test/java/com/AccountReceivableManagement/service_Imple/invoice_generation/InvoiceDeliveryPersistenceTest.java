package com.AccountReceivableManagement.service_Imple.invoice_generation;

import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.entity.invoice_generation.Invoice;
import com.AccountReceivableManagement.entity.invoice_generation.InvoiceDelivery;
import com.AccountReceivableManagement.entity.invoice_generation.InvoiceDocument;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.client.ClientRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceDeliveryRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceDocumentRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository;
import com.AccountReceivableManagement.repo.receivable.ReceivableRepository;
import com.AccountReceivableManagement.security.Actor;
import com.AccountReceivableManagement.security.FinancialAction;
import com.AccountReceivableManagement.security.FinancialActionAuthorizer;
import com.AccountReceivableManagement.service_Imple.receivable.ReceivableRecoveryServiceImpl;
import com.AccountReceivableManagement.service_Imple.receivable.ReceivableServiceImpl;
import com.AccountReceivableManagement.service_interface.email.EmailService;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * G-19/G-20/G-21 against a real (H2) database: the invoice row lock, the
 * committed PENDING claim, and uk_receivable_invoice. Email and invoice
 * rendering are stand-ins; the mail server is never contacted.
 */
@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InvoiceDeliveryPersistenceTest {

    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private InvoiceDeliveryRepository deliveryRepository;
    @Autowired private InvoiceDocumentRepository documentRepository;
    @Autowired private ReceivableRepository receivableRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private final EmailService emailService = mock(EmailService.class);
    private final InvoiceService invoiceService = mock(InvoiceService.class);
    private final FinancialActionAuthorizer authorizer = mock(FinancialActionAuthorizer.class);
    private final AtomicInteger emailsSent = new AtomicInteger();

    private TransactionTemplate tx;
    private ReceivableServiceImpl receivableService;
    private InvoiceDeliveryServiceImpl deliveryService;
    private ReceivableRecoveryServiceImpl receivableRecovery;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        emailsSent.set(0);
        when(authorizer.requirePermission(any(FinancialAction.class))).thenReturn(new Actor(9L, "finance@example.com"));
        when(invoiceService.getInvoiceById(any())).thenAnswer(invocation -> InvoiceResponseDto.builder()
                .invoiceId(invocation.getArgument(0)).invoiceNumber("INV-1").sellerLegalName("Paves")
                .grandTotal(new BigDecimal("118.00")).invoiceDate(LocalDate.of(2026, 11, 1)).build());
        when(emailService.sendEmailWithAttachment(anyString(), anyString(), anyString(), any(byte[].class),
                anyString(), anyString())).thenAnswer(invocation -> {
                    emailsSent.incrementAndGet();
                    Thread.sleep(50); // the SMTP round trip, outside any transaction
                    return "<id@mail>";
                });
        receivableService = new ReceivableServiceImpl(invoiceRepository, deliveryRepository, receivableRepository,
                transactionManager);
        deliveryService = new InvoiceDeliveryServiceImpl(invoiceRepository, clientRepository, deliveryRepository,
                invoiceService, documentRepository, emailService, receivableService, transactionManager, authorizer);
        receivableRecovery = new ReceivableRecoveryServiceImpl(receivableService, receivableRepository,
                invoiceRepository, deliveryRepository, authorizer);
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(status -> {
            receivableRepository.deleteAll();
            deliveryRepository.deleteAll();
            documentRepository.deleteAll();
            invoiceRepository.deleteAll();
        });
    }

    private UUID readyToSendInvoice() {
        Invoice invoice = tx.execute(status -> invoiceRepository.save(Invoice.builder()
                .invoiceNumber("INV-" + UUID.randomUUID().toString().substring(0, 8))
                .taxCalculationId(UUID.randomUUID())
                .clientId(UUID.randomUUID())
                .email("ap@client.example")
                .projectId(1L)
                .billingPeriodStart(LocalDate.of(2026, 10, 1))
                .billingPeriodEnd(LocalDate.of(2026, 10, 31))
                .invoiceDate(LocalDate.of(2026, 11, 1))
                .generatedAt(LocalDateTime.of(2026, 11, 1, 9, 0))
                .currencyCode("INR")
                .subtotal(new BigDecimal("100.00"))
                .totalTaxAmount(new BigDecimal("18.00"))
                .grandTotal(new BigDecimal("118.00"))
                .status(InvoiceStatus.READY_TO_SEND)
                .build()));
        tx.executeWithoutResult(status -> documentRepository.save(InvoiceDocument.builder()
                .invoiceId(invoice.getInvoiceId()).fileName("invoice.pdf").contentType("application/pdf")
                .content(new byte[]{37, 80, 68, 70}).sizeBytes(4L).checksumSha256("x".repeat(64))
                .generatedAt(LocalDateTime.now()).build()));
        return invoice.getInvoiceId();
    }

    private UUID sentInvoiceWithoutReceivable() {
        UUID invoiceId = readyToSendInvoice();
        tx.executeWithoutResult(status -> deliveryRepository.save(InvoiceDelivery.builder()
                .invoiceId(invoiceId).recipientEmail("ap@client.example").status(InvoiceDeliveryStatus.SENT)
                .sentAt(LocalDateTime.now()).build()));
        return invoiceId;
    }

    private <T> List<Object> concurrently(int threads, Callable<T> work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return work.call();
                    } catch (RuntimeException ex) {
                        return ex;
                    }
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    // ================================================================ G-19 / G-20

    @Test
    void concurrentSends_ofTheSameInvoice_sendOneEmail_andCreateOneReceivable() throws Exception {
        UUID invoiceId = readyToSendInvoice();

        List<Object> results = concurrently(6, () -> deliveryService.sendInvoice(invoiceId));

        assertThat(emailsSent.get()).isEqualTo(1);
        assertThat(results).filteredOn(r -> r instanceof GlobalExceptionHandler.ValidationException).hasSize(5);
        assertThat(deliveryRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId))
                .extracting(InvoiceDelivery::getStatus).containsExactly(InvoiceDeliveryStatus.SENT);
        assertThat(receivableRepository.findByInvoiceId(invoiceId)).isPresent();
        assertThat(receivableRepository.count()).isEqualTo(1);
    }

    @Test
    void invoiceNotReady_sendsNothing_andRecordsNothing() {
        UUID invoiceId = readyToSendInvoice();
        tx.executeWithoutResult(status -> {
            Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
            invoice.setStatus(InvoiceStatus.APPROVED);
            invoiceRepository.save(invoice);
        });

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> deliveryService.sendInvoice(invoiceId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class);

        assertThat(emailsSent.get()).isZero();
        assertThat(deliveryRepository.count()).isZero();
    }

    // ================================================================ G-21

    @Test
    void sentInvoiceWithoutReceivable_isReconciled_once_andRepeatsAreNoOps() {
        UUID invoiceId = sentInvoiceWithoutReceivable();

        assertThat(receivableRecovery.reconcileSentInvoicesWithoutReceivable(50)).isEqualTo(1);
        assertThat(receivableRecovery.reconcileSentInvoicesWithoutReceivable(50)).isZero();

        var receivable = receivableRepository.findByInvoiceId(invoiceId).orElseThrow();
        assertThat(receivable.getInvoiceAmount()).isEqualByComparingTo("118.00");
        assertThat(receivableRepository.count()).isEqualTo(1);
        assertThat(emailsSent.get()).isZero();
    }

    @Test
    void concurrentReconciliation_createsAtMostOneReceivablePerInvoice() throws Exception {
        UUID invoiceId = sentInvoiceWithoutReceivable();

        concurrently(5, () -> receivableRecovery.reconcileSentInvoicesWithoutReceivable(50));
        concurrently(5, () -> receivableRecovery.recoverReceivable(invoiceId, "support ticket"));

        assertThat(receivableRepository.count()).isEqualTo(1);
    }

    @Test
    void invoiceThatWasNeverSent_getsNoReceivable() {
        UUID invoiceId = readyToSendInvoice();
        tx.executeWithoutResult(status -> deliveryRepository.save(InvoiceDelivery.builder()
                .invoiceId(invoiceId).recipientEmail("ap@client.example").status(InvoiceDeliveryStatus.FAILED)
                .failedAt(LocalDateTime.now()).failureReason("refused").build()));

        assertThat(receivableRecovery.reconcileSentInvoicesWithoutReceivable(50)).isZero();
        var result = receivableRecovery.recoverReceivable(invoiceId, "check");

        assertThat(result.isAccepted()).isFalse();
        assertThat(result.getMessage()).contains("has not been sent");
        assertThat(receivableRepository.count()).isZero();
    }

    @Test
    void existingReceivable_isNotDuplicatedByOperatorRecovery() {
        UUID invoiceId = sentInvoiceWithoutReceivable();
        receivableRecovery.reconcileSentInvoicesWithoutReceivable(50);

        var result = receivableRecovery.recoverReceivable(invoiceId, "double check");

        assertThat(result.isAccepted()).isFalse();
        assertThat(result.getMessage()).contains("already has its receivable");
        assertThat(receivableRepository.count()).isEqualTo(1);
    }
}
