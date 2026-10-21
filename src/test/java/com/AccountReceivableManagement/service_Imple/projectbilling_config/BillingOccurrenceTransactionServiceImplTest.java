package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The scheduler's per-occurrence SCHEDULED -> TAX_PENDING transition acts on
 * the current, locked row - never on the possibly stale copy the scheduler
 * selected earlier.
 */
@ExtendWith(MockitoExtension.class)
class BillingOccurrenceTransactionServiceImplTest {

    @Mock
    private BillingScheduleRepository billingScheduleRepository;

    @InjectMocks
    private BillingOccurrenceTransactionServiceImpl transactionService;

    private BillingSchedule occurrence(BillingPeriodStatus status, LocalDate billingDate, String amount) {
        return BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(billingDate.withDayOfMonth(1))
                .periodEndDate(billingDate)
                .billingDate(billingDate)
                .billingAmount(new BigDecimal(amount))
                .periodStatus(status)
                .taxStatus(status == BillingPeriodStatus.SCHEDULED ? BillingPeriodStatus.PENDING : status)
                .isInvoiced(false)
                .isActive(true)
                .build();
    }

    private BillingSchedule copyOf(BillingSchedule source) {
        return BillingSchedule.builder()
                .billingScheduleId(source.getBillingScheduleId())
                .periodNumber(source.getPeriodNumber())
                .periodStartDate(source.getPeriodStartDate())
                .periodEndDate(source.getPeriodEndDate())
                .billingDate(source.getBillingDate())
                .billingAmount(source.getBillingAmount())
                .periodStatus(source.getPeriodStatus())
                .taxStatus(source.getTaxStatus())
                .isInvoiced(source.getIsInvoiced())
                .isActive(source.getIsActive())
                .build();
    }

    @Test
    void dueScheduledOccurrence_movesToTaxPending() {
        BillingSchedule current = occurrence(BillingPeriodStatus.SCHEDULED, LocalDate.now(), "1000.00");
        when(billingScheduleRepository.findByIdForUpdate(current.getBillingScheduleId()))
                .thenReturn(Optional.of(current));

        transactionService.transitionSingleOccurrenceToTaxPending(copyOf(current));

        assertThat(current.getPeriodStatus()).isEqualTo(BillingPeriodStatus.TAX_PENDING);
        assertThat(current.getTaxStatus()).isEqualTo(BillingPeriodStatus.TAX_PENDING);
        verify(billingScheduleRepository).save(current);
    }

    /** Overlapping or repeated scheduler runs: the second finds it already TAX_PENDING. */
    @Test
    void repeatedRun_transitionsOnlyOnce() {
        BillingSchedule current = occurrence(BillingPeriodStatus.SCHEDULED, LocalDate.now().minusDays(3), "1000.00");
        BillingSchedule selectedByRunA = copyOf(current);
        BillingSchedule selectedByRunB = copyOf(current);
        when(billingScheduleRepository.findByIdForUpdate(current.getBillingScheduleId()))
                .thenReturn(Optional.of(current));

        transactionService.transitionSingleOccurrenceToTaxPending(selectedByRunA);
        transactionService.transitionSingleOccurrenceToTaxPending(selectedByRunB);

        verify(billingScheduleRepository, times(1)).save(any());
        assertThat(current.getPeriodStatus()).isEqualTo(BillingPeriodStatus.TAX_PENDING);
    }

    /** Changed after selection (amount updated by reconciliation): the current values are kept. */
    @Test
    void staleSelectedCopy_neverOverwritesTheCurrentRow() {
        BillingSchedule current = occurrence(BillingPeriodStatus.SCHEDULED, LocalDate.now(), "1500.00");
        BillingSchedule staleSelection = copyOf(current);
        staleSelection.setBillingAmount(new BigDecimal("1000.00"));
        when(billingScheduleRepository.findByIdForUpdate(current.getBillingScheduleId()))
                .thenReturn(Optional.of(current));

        transactionService.transitionSingleOccurrenceToTaxPending(staleSelection);

        verify(billingScheduleRepository).save(current);
        verify(billingScheduleRepository, never()).save(staleSelection);
        assertThat(current.getBillingAmount()).isEqualByComparingTo("1500.00");
    }

    /** Deleted by reconciliation after selection: not resurrected. */
    @Test
    void occurrenceDeletedAfterSelection_isNotResurrected() {
        BillingSchedule staleSelection = occurrence(BillingPeriodStatus.SCHEDULED, LocalDate.now(), "1000.00");
        when(billingScheduleRepository.findByIdForUpdate(staleSelection.getBillingScheduleId()))
                .thenReturn(Optional.empty());

        transactionService.transitionSingleOccurrenceToTaxPending(staleSelection);

        verify(billingScheduleRepository, never()).save(any());
    }

    @Test
    void occurrenceDeactivatedAfterSelection_isLeftAlone() {
        BillingSchedule current = occurrence(BillingPeriodStatus.SCHEDULED, LocalDate.now(), "1000.00");
        BillingSchedule staleSelection = copyOf(current);
        current.setIsActive(false);
        when(billingScheduleRepository.findByIdForUpdate(current.getBillingScheduleId()))
                .thenReturn(Optional.of(current));

        transactionService.transitionSingleOccurrenceToTaxPending(staleSelection);

        verify(billingScheduleRepository, never()).save(any());
        assertThat(current.getPeriodStatus()).isEqualTo(BillingPeriodStatus.SCHEDULED);
    }

    @Test
    void futureOccurrence_isNotTransitioned() {
        BillingSchedule current = occurrence(BillingPeriodStatus.SCHEDULED, LocalDate.now().plusDays(1), "1000.00");
        when(billingScheduleRepository.findByIdForUpdate(current.getBillingScheduleId()))
                .thenReturn(Optional.of(current));

        transactionService.transitionSingleOccurrenceToTaxPending(copyOf(current));

        verify(billingScheduleRepository, never()).save(any());
    }
}
