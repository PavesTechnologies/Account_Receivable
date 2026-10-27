package com.AccountReceivableManagement.service_Imple.dashboard;

import com.AccountReceivableManagement.config.dashboard.ArDashboardProperties;
import com.AccountReceivableManagement.dto.dashboard.ActivityEventDto;
import com.AccountReceivableManagement.dto.dashboard.AgingBucketDto;
import com.AccountReceivableManagement.dto.dashboard.ArDashboardSummaryDto;
import com.AccountReceivableManagement.dto.dashboard.BillingModelSliceDto;
import com.AccountReceivableManagement.dto.dashboard.CurrencyAmountDto;
import com.AccountReceivableManagement.dto.dashboard.DashboardInvoiceRowDto;
import com.AccountReceivableManagement.dto.dashboard.DashboardInvoiceView;
import com.AccountReceivableManagement.dto.dashboard.DashboardPeriod;
import com.AccountReceivableManagement.dto.dashboard.PipelineStageDto;
import com.AccountReceivableManagement.dto.dashboard.TrendPointDto;
import com.AccountReceivableManagement.dto.dashboard.WorklistItemDto;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceApprovalAction;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.repo.dashboard.ArDashboardQueryRepository;
import com.AccountReceivableManagement.repo.dashboard.rows.ApprovalEventRow;
import com.AccountReceivableManagement.repo.dashboard.rows.BillingTypeTotalRow;
import com.AccountReceivableManagement.repo.dashboard.rows.ConfigEventRow;
import com.AccountReceivableManagement.repo.dashboard.rows.ConfigStatusCountRow;
import com.AccountReceivableManagement.repo.dashboard.rows.CurrencyTotalRow;
import com.AccountReceivableManagement.repo.dashboard.rows.DeliveryEventRow;
import com.AccountReceivableManagement.repo.dashboard.rows.InvoiceStatusTotalRow;
import com.AccountReceivableManagement.repo.dashboard.rows.LedgerRow;
import com.AccountReceivableManagement.repo.dashboard.rows.MonthTotalRow;
import com.AccountReceivableManagement.repo.dashboard.rows.OpenReceivableRow;
import com.AccountReceivableManagement.repo.dashboard.rows.ScheduleTotalRow;
import com.AccountReceivableManagement.repo.dashboard.rows.SnapshotStatusTotalRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Aggregation logic only — the repository is mocked (its JPQL needs a database, as elsewhere in AR). */
@ExtendWith(MockitoExtension.class)
class ArDashboardServiceImplTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 22);

    @Mock
    private ArDashboardQueryRepository repository;

    private ArDashboardProperties properties;
    private ArDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new ArDashboardProperties();
        Clock clock = Clock.fixed(AS_OF.atTime(9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new ArDashboardServiceImpl(repository, properties, clock);
    }

    // ------------------------------------------------------------------ period windows & buckets

    @Test
    void periodWindowsRunToDateAndCompareWithTheSameSpanOnePeriodEarlier() {
        assertThat(PeriodWindow.of(DashboardPeriod.MTD, AS_OF)).isEqualTo(new PeriodWindow(
                LocalDate.of(2026, 10, 1), AS_OF, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 22)));
        assertThat(PeriodWindow.of(DashboardPeriod.QTD, AS_OF)).isEqualTo(new PeriodWindow(
                LocalDate.of(2026, 10, 1), AS_OF, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 22)));
        assertThat(PeriodWindow.of(DashboardPeriod.YTD, AS_OF)).isEqualTo(new PeriodWindow(
                LocalDate.of(2026, 1, 1), AS_OF, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 10, 22)));
        // Month-end clamps instead of overflowing into the next month.
        assertThat(PeriodWindow.of(DashboardPeriod.MTD, LocalDate.of(2026, 3, 31)).priorEnd())
                .isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(PeriodWindow.of(DashboardPeriod.QTD, LocalDate.of(2026, 2, 14)).start())
                .isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void agingBucketBoundariesAreInclusive() {
        assertThat(AgingBucket.forDays(0)).isEqualTo(AgingBucket.CURRENT);
        assertThat(AgingBucket.forDays(30)).isEqualTo(AgingBucket.CURRENT);
        assertThat(AgingBucket.forDays(31)).isEqualTo(AgingBucket.D60);
        assertThat(AgingBucket.forDays(60)).isEqualTo(AgingBucket.D60);
        assertThat(AgingBucket.forDays(61)).isEqualTo(AgingBucket.D90);
        assertThat(AgingBucket.forDays(90)).isEqualTo(AgingBucket.D90);
        assertThat(AgingBucket.forDays(91)).isEqualTo(AgingBucket.D120);
        assertThat(AgingBucket.forDays(120)).isEqualTo(AgingBucket.D120);
        assertThat(AgingBucket.forDays(121)).isEqualTo(AgingBucket.D120_PLUS);
    }

    // ------------------------------------------------------------------ summary

    @Test
    void receivablesDriveOutstandingOverdueCriticalAndAgingPerCurrency() {
        when(repository.findOpenReceivables()).thenReturn(List.of(
                receivable("Acme", "USD", "100.00", "2026-10-01", "2026-10-31"),     // current, not due
                receivable("Solaria", "USD", "200.00", "2026-07-01", "2026-08-01"),  // 113 days old, 82 overdue
                receivable("Nexis", "INR", "1000.00", "2026-09-01", "2026-10-01"),   // 51 days old, 21 overdue
                receivable("Helix", "EUR", "50.00", null, null)));                   // cannot be aged

        ArDashboardSummaryDto summary = service.getSummary(DashboardPeriod.MTD, null);

        assertThat(summary.getAsOf()).isEqualTo(AS_OF);
        assertThat(summary.getKpis().getOutstanding().getCount()).isEqualTo(4);
        assertThat(amounts(summary.getKpis().getOutstanding().getAmounts()))
                .containsExactly(Map.entry("EUR", "50.00"), Map.entry("INR", "1000.00"), Map.entry("USD", "300.00"));
        assertThat(summary.getKpis().getOverdue().getCount()).isEqualTo(2);
        assertThat(amounts(summary.getKpis().getOverdue().getAmounts()))
                .containsExactly(Map.entry("INR", "1000.00"), Map.entry("USD", "200.00"));
        assertThat(summary.getKpis().getCriticalOverdue().getCount()).isEqualTo(1);
        assertThat(summary.getKpis().getCriticalOverdueDays()).isEqualTo(60);

        Map<String, AgingBucketDto> aging = summary.getAging().stream()
                .collect(Collectors.toMap(AgingBucketDto::getKey, Function.identity()));
        assertThat(summary.getAging()).extracting(AgingBucketDto::getKey)
                .containsExactly("current", "d60", "d90", "d120", "d120plus");
        assertThat(amounts(aging.get("current").getAmounts())).containsExactly(Map.entry("USD", "100.00"));
        assertThat(amounts(aging.get("d60").getAmounts())).containsExactly(Map.entry("INR", "1000.00"));
        assertThat(aging.get("d90").getCount()).isZero();
        assertThat(amounts(aging.get("d120").getAmounts())).containsExactly(Map.entry("USD", "200.00"));
        assertThat(aging.get("d120plus").getMaxDays()).isNull();
        assertThat(summary.getAgingUnknownDateCount()).isEqualTo(1);

        WorklistItemDto overdueItem = worklist(summary, "overdue-critical");
        assertThat(overdueItem.getCount()).isEqualTo(1);
        assertThat(overdueItem.getClientNames()).containsExactly("Solaria");
        assertThat(overdueItem.getOldestDays()).isEqualTo(82);
    }

    @Test
    void collectionsAreAlwaysReportedAsNotTracked() {
        ArDashboardSummaryDto summary = service.getSummary(DashboardPeriod.YTD, AS_OF);

        assertThat(summary.getKpis().getCollections().isTracked()).isFalse();
        assertThat(summary.getKpis().getCollections().getReason()).contains("No payment or receipt data");
        assertThat(summary.getKpis().getCollections().getCollected()).isNull();
        assertThat(summary.getKpis().getCollections().getDsoDays()).isNull();
        assertThat(summary.getKpis().getCollections().getCollectionRate()).isNull();
        assertThat(summary.getTrend()).allSatisfy(point -> assertThat(point.getCollected()).isNull());
        assertThat(summary.getNotes()).isNotEmpty();
    }

    @Test
    void pipelineFollowsTheBillingCycleWithCountsAmountsAndWaitTimes() {
        when(repository.countConfigurationsByStatus()).thenReturn(List.of(
                new ConfigStatusCountRow(ApprovalStatus.DRAFT, BillingConfigurationStatus.INACTIVE, 2L),
                new ConfigStatusCountRow(ApprovalStatus.PENDING_APPROVAL, BillingConfigurationStatus.INACTIVE, 3L),
                new ConfigStatusCountRow(ApprovalStatus.APPROVED, BillingConfigurationStatus.ACTIVE, 5L)));
        when(repository.findOldestPendingConfigurationUpdate()).thenReturn(LocalDateTime.of(2026, 10, 18, 10, 0));
        when(repository.sumSnapshotsByStatus()).thenReturn(List.of(
                new SnapshotStatusTotalRow(BillingSnapshotStatus.READY_FOR_TAX, "USD", bd("500"), 2L,
                        LocalDateTime.of(2026, 10, 20, 8, 0)),
                new SnapshotStatusTotalRow(BillingSnapshotStatus.INVOICED, "USD", bd("9999"), 9L, null)));
        when(repository.sumDueUninvoicedSchedules(AS_OF)).thenReturn(List.of(
                new ScheduleTotalRow(BillingPeriodStatus.TAX_PENDING, "INR", bd("300"), 1L, LocalDate.of(2026, 10, 15)),
                new ScheduleTotalRow(BillingPeriodStatus.TAX_CALCULATED, "USD", bd("50"), 1L, LocalDate.of(2026, 10, 1))));
        when(repository.sumSnapshotsReadyForInvoice()).thenReturn(List.of(new CurrencyTotalRow("USD", bd("70"), 1L)));
        when(repository.sumInvoicesByStatus()).thenReturn(List.of(
                new InvoiceStatusTotalRow(InvoiceStatus.GENERATED, "USD", bd("10"), 1L),
                new InvoiceStatusTotalRow(InvoiceStatus.PENDING_APPROVAL, "USD", bd("900"), 2L)));
        when(repository.findOldestGeneratedAt()).thenReturn(LocalDateTime.of(2026, 10, 21, 12, 0));
        when(repository.findPendingApprovalSubmittedTimes()).thenReturn(List.of(
                LocalDateTime.of(2026, 10, 20, 9, 0), LocalDateTime.of(2026, 10, 17, 9, 0)));
        when(repository.findUnbilledProjectIds(AS_OF)).thenReturn(Set.of(1L, 2L));

        ArDashboardSummaryDto summary = service.getSummary(DashboardPeriod.MTD, AS_OF);
        Map<String, PipelineStageDto> stages = summary.getPipeline().stream()
                .collect(Collectors.toMap(PipelineStageDto::getId, Function.identity()));

        assertThat(summary.getPipeline()).extracting(PipelineStageDto::getId)
                .containsExactly("setup", "setupApproval", "acquisition", "tax", "generation", "invoiceApproval");

        assertThat(stages.get("setup").getCount()).isEqualTo(10);
        assertThat(stages.get("setup").getBreakdown())
                .containsEntry("active", 5L).containsEntry("inactive", 5L)
                .containsEntry("draft", 2L).containsEntry("pendingApproval", 3L).containsEntry("approved", 5L);
        assertThat(stages.get("setupApproval").getCount()).isEqualTo(3);
        assertThat(stages.get("setupApproval").getOldestWaitingDays()).isEqualTo(4);

        assertThat(stages.get("acquisition").getCount()).isZero();

        assertThat(stages.get("tax").getCount()).isEqualTo(3);
        assertThat(amounts(stages.get("tax").getAmounts()))
                .containsExactly(Map.entry("INR", "300.00"), Map.entry("USD", "500.00"));
        assertThat(stages.get("tax").getOldestWaitingDays()).isEqualTo(7); // schedule billed 15 Oct
        assertThat(stages.get("tax").getBreakdown())
                .containsEntry("snapshotsReadyForTax", 2L).containsEntry("schedulesAwaitingTax", 1L);

        assertThat(stages.get("generation").getCount()).isEqualTo(3);
        assertThat(amounts(stages.get("generation").getAmounts())).containsExactly(Map.entry("USD", "130.00"));
        assertThat(stages.get("generation").getBreakdown())
                .containsEntry("snapshotsReadyForInvoice", 1L)
                .containsEntry("schedulesReadyForInvoice", 1L)
                .containsEntry("generatedInvoices", 1L);

        assertThat(stages.get("invoiceApproval").getCount()).isEqualTo(2);
        assertThat(stages.get("invoiceApproval").getOldestWaitingDays()).isEqualTo(5);

        // WIP = awaiting tax + ready for invoice (not the already-generated invoice).
        assertThat(summary.getKpis().getUnbilledWip().getCount()).isEqualTo(5);
        assertThat(amounts(summary.getKpis().getUnbilledWip().getAmounts()))
                .containsExactly(Map.entry("INR", "300.00"), Map.entry("USD", "620.00"));
        assertThat(summary.getKpis().getUnbilledProjectCount()).isEqualTo(2);
        assertThat(summary.getKpis().getPendingApproval().getCount()).isEqualTo(2);

        assertThat(worklist(summary, "setup-approvals").getCount()).isEqualTo(3);
        assertThat(worklist(summary, "tax-ready").getCount()).isEqualTo(3);
        assertThat(worklist(summary, "draft-queue").getOldestDays()).isEqualTo(1);
    }

    @Test
    void invoicedFiguresUseThePeriodAndPriorWindows() {
        // 22 Oct is in Q4: QTD = 1–22 Oct, prior = 1–22 Jul.
        when(repository.sumInvoicedBetween(LocalDate.of(2026, 10, 1), AS_OF))
                .thenReturn(List.of(new CurrencyTotalRow("USD", bd("1200"), 4L)));
        when(repository.sumInvoicedBetween(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 22)))
                .thenReturn(List.of(new CurrencyTotalRow("USD", bd("800"), 3L)));

        ArDashboardSummaryDto summary = service.getSummary(DashboardPeriod.QTD, AS_OF);

        assertThat(summary.getPeriodStart()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(summary.getPriorPeriodStart()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(summary.getKpis().getInvoicedInPeriod().getCount()).isEqualTo(4);
        assertThat(summary.getKpis().getInvoicedPriorPeriod().getCount()).isEqualTo(3);
        assertThat(amounts(summary.getKpis().getInvoicedPriorPeriod().getAmounts()))
                .containsExactly(Map.entry("USD", "800.00"));
    }

    @Test
    void trendCoversTheConfiguredMonthsAndFillsEmptyOnes() {
        when(repository.sumInvoicedByMonth(LocalDate.of(2026, 5, 1), AS_OF)).thenReturn(List.of(
                new MonthTotalRow(2026, 9, "USD", bd("100"), 2L),
                new MonthTotalRow(2026, 9, "INR", bd("40"), 1L)));

        List<TrendPointDto> trend = service.getSummary(DashboardPeriod.MTD, AS_OF).getTrend();

        assertThat(trend).extracting(TrendPointDto::getMonth)
                .containsExactly("2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10");
        assertThat(trend.get(4).getInvoiceCount()).isEqualTo(3);
        assertThat(amounts(trend.get(4).getInvoiced()))
                .containsExactly(Map.entry("INR", "40.00"), Map.entry("USD", "100.00"));
        assertThat(trend.get(5).getInvoiced()).isEmpty();
    }

    @Test
    void billingModelMixGroupsByTypeLabelsUnknownsAndSortsByVolume() {
        when(repository.sumInvoicedByBillingType(LocalDate.of(2026, 10, 1), AS_OF)).thenReturn(List.of(
                new BillingTypeTotalRow(null, "USD", bd("100"), 1L),
                new BillingTypeTotalRow("Time & Material", "USD", bd("300"), 3L),
                new BillingTypeTotalRow("Time & Material", "INR", bd("50"), 1L)));

        List<BillingModelSliceDto> mix = service.getSummary(DashboardPeriod.MTD, AS_OF).getBillingModelMix();

        assertThat(mix).extracting(BillingModelSliceDto::getBillingType)
                .containsExactly("Time & Material", ArDashboardServiceImpl.UNSPECIFIED_BILLING_TYPE);
        assertThat(mix.get(0).getCount()).isEqualTo(4);
        assertThat(amounts(mix.get(0).getAmounts()))
                .containsExactly(Map.entry("INR", "50.00"), Map.entry("USD", "300.00"));
    }

    @Test
    void recentActivityMergesSourcesNewestFirstUpToTheLimit() {
        properties.setActivityLimit(2);
        when(repository.findRecentApprovalEvents(2)).thenReturn(List.of(new ApprovalEventRow(
                InvoiceApprovalAction.APPROVED, "INV-1", "SYSTEM", LocalDateTime.of(2026, 10, 20, 10, 0))));
        when(repository.findRecentDeliveryEvents(2)).thenReturn(List.of(new DeliveryEventRow(
                InvoiceDeliveryStatus.SENT, "INV-2", LocalDateTime.of(2026, 10, 21, 10, 0), null,
                LocalDateTime.of(2026, 10, 21, 10, 0))));
        when(repository.findRecentConfigurationEvents(2)).thenReturn(List.of(new ConfigEventRow(
                ApprovalStatus.PENDING_APPROVAL, "AR project", LocalDateTime.of(2026, 10, 19, 10, 0))));

        List<ActivityEventDto> activity = service.getSummary(DashboardPeriod.MTD, AS_OF).getRecentActivity();

        assertThat(activity).extracting(ActivityEventDto::getTitle)
                .containsExactly("Invoice sent to client", "Invoice approved");
        assertThat(activity.get(1).getReference()).isEqualTo("INV-1");
        assertThat(activity.get(1).getActor()).isEqualTo("SYSTEM");
    }

    // ------------------------------------------------------------------ ledger

    @Test
    void ledgerDerivesDisplayStatusAndAgingFromTheReceivable() {
        LedgerRow overdue = ledger(InvoiceStatus.APPROVED, true, "250.00", "2026-09-01", "2026-10-01");
        LedgerRow issued = ledger(InvoiceStatus.APPROVED, true, "90.00", "2026-10-15", "2026-11-14");
        LedgerRow awaitingSend = ledger(InvoiceStatus.APPROVED, false, null, "2026-10-20", "2026-11-19");
        LedgerRow pending = ledger(InvoiceStatus.PENDING_APPROVAL, false, null, "2026-10-21", "2026-11-20");
        when(repository.findLedger(DashboardInvoiceView.ALL, null, AS_OF, 50))
                .thenReturn(List.of(overdue, issued, awaitingSend, pending));

        List<DashboardInvoiceRowDto> rows = service.getInvoices(null, "  ", null, null);

        assertThat(rows).extracting(DashboardInvoiceRowDto::getDisplayStatus)
                .containsExactly("OVERDUE", "ISSUED", "AWAITING_SEND", "PENDING_APPROVAL");
        assertThat(rows.get(0).getDaysOverdue()).isEqualTo(21);
        assertThat(rows.get(0).getDaysOutstanding()).isEqualTo(51);
        assertThat(rows.get(1).getDaysOverdue()).isZero();
        assertThat(rows.get(2).isSent()).isFalse();
        assertThat(rows.get(2).getOutstandingBalance()).isNull();
        assertThat(rows.get(2).getDaysOverdue()).isNull();
    }

    @Test
    void ledgerClampsTheLimitLowerCasesTheSearchAndRejectsNonPositiveLimits() {
        service.getInvoices(DashboardInvoiceView.OVERDUE, " Acme ", 500, AS_OF);
        verify(repository).findLedger(eq(DashboardInvoiceView.OVERDUE), eq("%acme%"), eq(AS_OF), eq(200));

        assertThatThrownBy(() -> service.getInvoices(DashboardInvoiceView.ALL, null, 0, AS_OF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void displayStatusForUnsentInvoicesMirrorsTheWorkflowStatus() {
        assertThat(ArDashboardServiceImpl.displayStatus(InvoiceStatus.GENERATED, false, false, null, AS_OF))
                .isEqualTo("GENERATED");
        assertThat(ArDashboardServiceImpl.displayStatus(InvoiceStatus.REJECTED, false, false, null, AS_OF))
                .isEqualTo("REJECTED");
        // Sent and fully settled (no open balance) is ISSUED even when past due.
        assertThat(ArDashboardServiceImpl.displayStatus(InvoiceStatus.APPROVED, true, false,
                LocalDate.of(2026, 1, 1), AS_OF)).isEqualTo("ISSUED");
    }

    // ------------------------------------------------------------------ helpers

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static OpenReceivableRow receivable(String client, String currency, String amount, String invoiceDate, String dueDate) {
        return new OpenReceivableRow(UUID.randomUUID(), "INV-" + client, client, currency,
                invoiceDate == null ? null : LocalDate.parse(invoiceDate),
                dueDate == null ? null : LocalDate.parse(dueDate),
                bd(amount));
    }

    private static LedgerRow ledger(InvoiceStatus status, boolean sent, String balance, String invoiceDate, String dueDate) {
        return new LedgerRow(UUID.randomUUID(), "INV", "Client", "Project", "Time & Material",
                LocalDate.parse(invoiceDate), LocalDate.parse(dueDate), "USD",
                bd("100"), bd("18"), bd("118"), status,
                sent ? UUID.randomUUID() : null, balance == null ? null : bd(balance),
                sent ? LocalDate.parse(dueDate) : null);
    }

    private static List<Map.Entry<String, String>> amounts(List<CurrencyAmountDto> amounts) {
        return amounts.stream().map(a -> Map.entry(a.getCurrencyCode(), a.getAmount().toPlainString())).toList();
    }

    private static WorklistItemDto worklist(ArDashboardSummaryDto summary, String id) {
        return summary.getWorklist().stream().filter(w -> w.getId().equals(id)).findFirst().orElseThrow();
    }
}
