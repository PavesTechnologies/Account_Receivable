package com.AccountReceivableManagement.service_Imple.receivable;

import com.AccountReceivableManagement.dto.receivable.ReceivableResponseDto;
import com.AccountReceivableManagement.entity.invoice_generation.Invoice;
import com.AccountReceivableManagement.entity.receivable.Receivable;
import com.AccountReceivableManagement.entity_enums.receivable.ReceivableStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceDeliveryRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository;
import com.AccountReceivableManagement.repo.receivable.ReceivableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Story 11.2 monitoring logic. The outstanding query's SQL itself
 * (balance &gt; 0, filters, ordering) is defined on
 * {@code ReceivableRepository.findOutstanding} and needs a database to
 * exercise; these tests cover what the service does with its results.
 */
@ExtendWith(MockitoExtension.class)
class ReceivableServiceImplTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceDeliveryRepository invoiceDeliveryRepository;

    @Mock
    private ReceivableRepository receivableRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ReceivableServiceImpl receivableService;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        receivableService = new ReceivableServiceImpl(
                invoiceRepository, invoiceDeliveryRepository, receivableRepository, transactionManager);
    }

    private Receivable receivable(LocalDate invoiceDate, LocalDate dueDate,
                                  String balance, ReceivableStatus status) {
        return Receivable.builder()
                .receivableId(UUID.randomUUID())
                .invoice(Invoice.builder().invoiceId(UUID.randomUUID()).build())
                .invoiceNumber("INV-1001")
                .clientId(UUID.randomUUID())
                .clientName("Acme")
                .projectId(20L)
                .projectName("Portal")
                .currencyCode("INR")
                .invoiceDate(invoiceDate)
                .dueDate(dueDate)
                .invoiceAmount(new BigDecimal("65000.00"))
                .outstandingBalance(new BigDecimal(balance))
                .status(status)
                .build();
    }

    @Test
    void outstanding_mapsRowsAndComputesDaysOutstandingFromInvoiceDate() {
        Receivable open = receivable(today.minusDays(9), today.plusDays(21), "65000.00",
                ReceivableStatus.OUTSTANDING);
        Receivable partial = receivable(today.minusDays(40), today.minusDays(10), "45000.00",
                ReceivableStatus.PARTIALLY_PAID);
        when(receivableRepository.findOutstanding(null, null, null)).thenReturn(List.of(open, partial));

        List<ReceivableResponseDto> result = receivableService.getOutstandingReceivables(null, null, null);

        assertThat(result).hasSize(2);
        ReceivableResponseDto first = result.get(0);
        assertThat(first.getReceivableId()).isEqualTo(open.getReceivableId());
        assertThat(first.getInvoiceId()).isEqualTo(open.getInvoice().getInvoiceId());
        assertThat(first.getInvoiceAmount()).isEqualByComparingTo("65000.00");
        assertThat(first.getOutstandingBalance()).isEqualByComparingTo("65000.00");
        assertThat(first.getDaysOutstanding()).isEqualTo(9L);
        assertThat(first.getDaysOverdue()).isEqualTo(0L);

        ReceivableResponseDto second = result.get(1);
        assertThat(second.getStatus()).isEqualTo(ReceivableStatus.PARTIALLY_PAID);
        assertThat(second.getOutstandingBalance()).isEqualByComparingTo("45000.00");
        assertThat(second.getDaysOutstanding()).isEqualTo(40L);
        assertThat(second.getDaysOverdue()).isEqualTo(10L);
    }

    @Test
    void outstanding_futureInvoiceDateNeverGivesNegativeDays_andMissingDueDateGivesNullOverdue() {
        Receivable future = receivable(today.plusDays(3), null, "100.00", ReceivableStatus.OUTSTANDING);
        when(receivableRepository.findOutstanding(null, null, null)).thenReturn(List.of(future));

        ReceivableResponseDto result = receivableService.getOutstandingReceivables(null, null, null).get(0);

        assertThat(result.getDaysOutstanding()).isZero();
        assertThat(result.getDaysOverdue()).isNull();
    }

    @Test
    void outstanding_passesClientProjectAndStatusFiltersToTheQuery() {
        UUID clientId = UUID.randomUUID();
        when(receivableRepository.findOutstanding(clientId, 20L, ReceivableStatus.PARTIALLY_PAID))
                .thenReturn(List.of());

        assertThat(receivableService.getOutstandingReceivables(
                clientId, 20L, ReceivableStatus.PARTIALLY_PAID)).isEmpty();

        verify(receivableRepository).findOutstanding(clientId, 20L, ReceivableStatus.PARTIALLY_PAID);
    }

    @Test
    void byId_returnsZeroBalanceReceivable_withNoDaysOutstanding() {
        Receivable settled = receivable(today.minusDays(30), today, "0.00", ReceivableStatus.SETTLED);
        when(receivableRepository.findById(settled.getReceivableId())).thenReturn(Optional.of(settled));

        ReceivableResponseDto result = receivableService.getReceivableById(settled.getReceivableId());

        assertThat(result.getStatus()).isEqualTo(ReceivableStatus.SETTLED);
        assertThat(result.getOutstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(result.getDaysOutstanding()).isNull();
        assertThat(result.getDaysOverdue()).isNull();
    }

    @Test
    void byId_missingReceivable_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(receivableRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> receivableService.getReceivableById(id))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class);
    }

    @Test
    void negativeBalance_isRejectedByTheEntityOnPersistAndUpdate() {
        Receivable negative = receivable(today, today, "-5000.00", ReceivableStatus.OUTSTANDING);

        assertThatThrownBy(negative::prePersist).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(negative::preUpdate).isInstanceOf(IllegalStateException.class);
    }

    private static final com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus SENT =
            com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus.SENT;
    private static final com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus SIMULATED =
            com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus.SIMULATED;

    private Invoice approvedInvoice(UUID id) {
        return Invoice.builder().invoiceId(id)
                .status(com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus.APPROVED).build();
    }

    @Test
    void create_invoiceNeverSentOrSimulated_isRefused() {
        UUID id = UUID.randomUUID();
        when(invoiceRepository.findByIdForUpdate(id)).thenReturn(Optional.of(approvedInvoice(id)));
        when(invoiceDeliveryRepository.existsByInvoiceIdAndStatus(id, SENT)).thenReturn(false);
        when(invoiceDeliveryRepository.existsByInvoiceIdAndStatus(id, SIMULATED)).thenReturn(false);

        assertThatThrownBy(() -> receivableService.createReceivableFromInvoice(id))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("has not been sent");
    }

    @Test
    void create_invoiceWithSimulatedDelivery_passesTheDispatchedCheck() {
        UUID id = UUID.randomUUID();
        Receivable existing = receivable(today, today.plusDays(30), "100.00", ReceivableStatus.OUTSTANDING);
        when(invoiceRepository.findByIdForUpdate(id)).thenReturn(Optional.of(approvedInvoice(id)));
        when(invoiceDeliveryRepository.existsByInvoiceIdAndStatus(id, SENT)).thenReturn(false);
        when(invoiceDeliveryRepository.existsByInvoiceIdAndStatus(id, SIMULATED)).thenReturn(true);
        when(receivableRepository.findByInvoiceId(id)).thenReturn(Optional.of(existing));

        assertThat(receivableService.createReceivableFromInvoice(id).getReceivableId())
                .isEqualTo(existing.getReceivableId());
    }

    @Test
    void create_invoiceWithSentDelivery_neverConsultsTheSimulatedStatus() {
        UUID id = UUID.randomUUID();
        Receivable existing = receivable(today, today.plusDays(30), "100.00", ReceivableStatus.OUTSTANDING);
        when(invoiceRepository.findByIdForUpdate(id)).thenReturn(Optional.of(approvedInvoice(id)));
        when(invoiceDeliveryRepository.existsByInvoiceIdAndStatus(id, SENT)).thenReturn(true);
        when(receivableRepository.findByInvoiceId(id)).thenReturn(Optional.of(existing));

        receivableService.createReceivableFromInvoice(id);

        verify(invoiceDeliveryRepository, org.mockito.Mockito.never()).existsByInvoiceIdAndStatus(id, SIMULATED);
    }

    @Test
    void create_onlySentOrSimulatedDeliveriesCanSatisfyTheDispatchedCheck() {
        UUID id = UUID.randomUUID();
        when(invoiceRepository.findByIdForUpdate(id)).thenReturn(Optional.of(approvedInvoice(id)));
        when(invoiceDeliveryRepository.existsByInvoiceIdAndStatus(id, SENT)).thenReturn(false);
        when(invoiceDeliveryRepository.existsByInvoiceIdAndStatus(id, SIMULATED)).thenReturn(false);

        assertThatThrownBy(() -> receivableService.createReceivableFromInvoice(id))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class);

        // a FAILED or PENDING delivery is never consulted, so it can never allow a receivable
        for (com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus other
                : com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus.values()) {
            if (other != SENT && other != SIMULATED) {
                verify(invoiceDeliveryRepository, org.mockito.Mockito.never()).existsByInvoiceIdAndStatus(id, other);
            }
        }
    }
}
