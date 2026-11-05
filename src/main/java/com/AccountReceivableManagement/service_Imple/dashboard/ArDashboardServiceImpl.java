package com.AccountReceivableManagement.service_Imple.dashboard;

import com.AccountReceivableManagement.config.dashboard.ArDashboardProperties;
import com.AccountReceivableManagement.dto.dashboard.ActivityEventDto;
import com.AccountReceivableManagement.dto.dashboard.AgingBucketDto;
import com.AccountReceivableManagement.dto.dashboard.ArDashboardSummaryDto;
import com.AccountReceivableManagement.dto.dashboard.BillingModelSliceDto;
import com.AccountReceivableManagement.dto.dashboard.CollectionsDto;
import com.AccountReceivableManagement.dto.dashboard.DashboardInvoiceRowDto;
import com.AccountReceivableManagement.dto.dashboard.DashboardInvoiceView;
import com.AccountReceivableManagement.dto.dashboard.DashboardKpisDto;
import com.AccountReceivableManagement.dto.dashboard.DashboardPeriod;
import com.AccountReceivableManagement.dto.dashboard.PipelineStageDto;
import com.AccountReceivableManagement.dto.dashboard.TrendPointDto;
import com.AccountReceivableManagement.dto.dashboard.WorklistItemDto;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
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
import com.AccountReceivableManagement.service_interface.dashboard.ArDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Builds the AR dashboard from aggregate queries. Figures that AR cannot
 * compute yet (anything needing payment data) are returned as null with
 * {@code collections.tracked = false}; money is always per currency.
 */
@Service
@Transactional(readOnly = true)
public class ArDashboardServiceImpl implements ArDashboardService {

    static final String COLLECTIONS_NOT_TRACKED =
            "No payment or receipt data is recorded in AR yet, so collected cash, collection rate and DSO cannot be calculated.";
    static final String UNSPECIFIED_BILLING_TYPE = "Unspecified";

    private static final Set<BillingPeriodStatus> SCHEDULE_AWAITING_TAX =
            Set.of(BillingPeriodStatus.PENDING, BillingPeriodStatus.SCHEDULED, BillingPeriodStatus.TAX_PENDING);

    private final ArDashboardQueryRepository repository;
    private final ArDashboardProperties properties;
    private final Clock clock;

    @Autowired
    public ArDashboardServiceImpl(ArDashboardQueryRepository repository, ArDashboardProperties properties) {
        this(repository, properties, Clock.systemDefaultZone());
    }

    ArDashboardServiceImpl(ArDashboardQueryRepository repository, ArDashboardProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    // =====================================================================================================  summary

    @Override
    public ArDashboardSummaryDto getSummary(DashboardPeriod period, LocalDate asOfOrNull) {
        DashboardPeriod effectivePeriod = period == null ? DashboardPeriod.MTD : period;
        LocalDate asOf = asOfOrNull == null ? LocalDate.now(clock) : asOfOrNull;
        PeriodWindow window = PeriodWindow.of(effectivePeriod, asOf);
        int criticalDays = properties.getCriticalOverdueDays();

        // --- receivables: outstanding, overdue, critical, aging
        MoneyTotals outstanding = new MoneyTotals();
        MoneyTotals overdue = new MoneyTotals();
        MoneyTotals critical = new MoneyTotals();
        Map<AgingBucket, MoneyTotals> aging = new EnumMap<>(AgingBucket.class);
        for (AgingBucket bucket : AgingBucket.values()) aging.put(bucket, new MoneyTotals());
        long unknownAge = 0;
        List<OverdueItem> criticalItems = new ArrayList<>();

        for (OpenReceivableRow row : repository.findOpenReceivables()) {
            outstanding.add(row.currencyCode(), row.outstandingBalance());
            if (row.invoiceDate() != null) {
                long age = Math.max(0, ChronoUnit.DAYS.between(row.invoiceDate(), asOf));
                aging.get(AgingBucket.forDays(age)).add(row.currencyCode(), row.outstandingBalance());
            } else {
                unknownAge++;
            }
            if (row.dueDate() != null && row.dueDate().isBefore(asOf)) {
                overdue.add(row.currencyCode(), row.outstandingBalance());
                long daysOverdue = ChronoUnit.DAYS.between(row.dueDate(), asOf);
                if (daysOverdue > criticalDays) {
                    critical.add(row.currencyCode(), row.outstandingBalance());
                    criticalItems.add(new OverdueItem(row.clientName(), daysOverdue));
                }
            }
        }

        // --- invoices by status
        Map<InvoiceStatus, MoneyTotals> invoicesByStatus = new EnumMap<>(InvoiceStatus.class);
        for (InvoiceStatusTotalRow row : repository.sumInvoicesByStatus()) {
            if (row.status() == null) continue;
            invoicesByStatus.computeIfAbsent(row.status(), s -> new MoneyTotals())
                    .add(row.currencyCode(), row.amount(), nz(row.count()));
        }
        MoneyTotals pendingApproval = invoicesByStatus.getOrDefault(InvoiceStatus.PENDING_APPROVAL, new MoneyTotals());
        MoneyTotals generated = invoicesByStatus.getOrDefault(InvoiceStatus.GENERATED, new MoneyTotals());

        // --- billing configurations
        Map<ApprovalStatus, Long> configsByApproval = new EnumMap<>(ApprovalStatus.class);
        Map<BillingConfigurationStatus, Long> configsByBilling = new EnumMap<>(BillingConfigurationStatus.class);
        long configTotal = 0;
        for (ConfigStatusCountRow row : repository.countConfigurationsByStatus()) {
            long n = nz(row.count());
            configTotal += n;
            if (row.approvalStatus() != null) configsByApproval.merge(row.approvalStatus(), n, Long::sum);
            if (row.billingStatus() != null) configsByBilling.merge(row.billingStatus(), n, Long::sum);
        }
        long pendingConfigs = configsByApproval.getOrDefault(ApprovalStatus.PENDING_APPROVAL, 0L);
        Long pendingConfigOldestDays = pendingConfigs > 0
                ? daysSince(repository.findOldestPendingConfigurationUpdate(), asOf) : null;

        // --- snapshots & schedules
        Map<BillingSnapshotStatus, MoneyTotals> snapshots = new EnumMap<>(BillingSnapshotStatus.class);
        Map<BillingSnapshotStatus, LocalDateTime> snapshotOldest = new EnumMap<>(BillingSnapshotStatus.class);
        for (SnapshotStatusTotalRow row : repository.sumSnapshotsByStatus()) {
            if (row.status() == null) continue;
            snapshots.computeIfAbsent(row.status(), s -> new MoneyTotals()).add(row.currencyCode(), row.amount(), nz(row.count()));
            if (row.oldestCreated() != null) {
                snapshotOldest.merge(row.status(), row.oldestCreated(), (a, b) -> a.isBefore(b) ? a : b);
            }
        }
        MoneyTotals snapshotsReadyForInvoice = sumRows(repository.sumSnapshotsReadyForInvoice());

        MoneyTotals schedulesAwaitingTax = new MoneyTotals();
        MoneyTotals schedulesReady = new MoneyTotals();
        LocalDate schedulesAwaitingTaxOldest = null;
        for (ScheduleTotalRow row : repository.sumDueUninvoicedSchedules(asOf)) {
            if (row.taxStatus() == BillingPeriodStatus.TAX_CALCULATED) {
                schedulesReady.add(row.currencyCode(), row.amount(), nz(row.count()));
            } else if (row.taxStatus() != null && SCHEDULE_AWAITING_TAX.contains(row.taxStatus())) {
                schedulesAwaitingTax.add(row.currencyCode(), row.amount(), nz(row.count()));
                schedulesAwaitingTaxOldest = earliest(schedulesAwaitingTaxOldest, row.oldestBillingDate());
            }
        }

        MoneyTotals snapshotsReadyForTax = snapshots.getOrDefault(BillingSnapshotStatus.READY_FOR_TAX, new MoneyTotals());
        MoneyTotals snapshotsInTax = snapshots.getOrDefault(BillingSnapshotStatus.IN_TAX, new MoneyTotals());
        MoneyTotals taxStage = new MoneyTotals().addAll(snapshotsReadyForTax).addAll(snapshotsInTax).addAll(schedulesAwaitingTax);
        Long taxOldestDays = daysSince(earliest(
                earliest(toDate(snapshotOldest.get(BillingSnapshotStatus.READY_FOR_TAX)),
                        toDate(snapshotOldest.get(BillingSnapshotStatus.IN_TAX))),
                schedulesAwaitingTaxOldest), asOf);

        MoneyTotals readyForInvoice = new MoneyTotals().addAll(snapshotsReadyForInvoice).addAll(schedulesReady);
        MoneyTotals generationStage = new MoneyTotals().addAll(readyForInvoice).addAll(generated);
        Long oldestGeneratedDays = generated.count() > 0 ? daysSince(repository.findOldestGeneratedAt(), asOf) : null;

        MoneyTotals unbilledWip = new MoneyTotals().addAll(taxStage).addAll(readyForInvoice);
        long unbilledProjects = unbilledWip.count() > 0 ? repository.findUnbilledProjectIds(asOf).size() : 0;

        Long invoiceApprovalOldestDays = pendingApproval.count() > 0
                ? daysSince(repository.findPendingApprovalSubmittedTimes().stream()
                        .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null), asOf)
                : null;

        // --- pipeline
        Map<String, Long> setupBreakdown = new LinkedHashMap<>();
        setupBreakdown.put("active", configsByBilling.getOrDefault(BillingConfigurationStatus.ACTIVE, 0L));
        setupBreakdown.put("inactive", configsByBilling.getOrDefault(BillingConfigurationStatus.INACTIVE, 0L));
        setupBreakdown.put("expired", configsByBilling.getOrDefault(BillingConfigurationStatus.EXPIRED, 0L));
        setupBreakdown.put("draft", configsByApproval.getOrDefault(ApprovalStatus.DRAFT, 0L));
        setupBreakdown.put("pendingApproval", pendingConfigs);
        setupBreakdown.put("approved", configsByApproval.getOrDefault(ApprovalStatus.APPROVED, 0L));
        setupBreakdown.put("rejected", configsByApproval.getOrDefault(ApprovalStatus.REJECTED, 0L));

        MoneyTotals acquisition = snapshots.getOrDefault(BillingSnapshotStatus.IN_PROGRESS, new MoneyTotals());

        List<PipelineStageDto> pipeline = List.of(
                stage("setup", "Billing Setup", configTotal, new MoneyTotals(), null, setupBreakdown),
                stage("setupApproval", "Setup Approval", pendingConfigs, new MoneyTotals(), pendingConfigOldestDays, Map.of()),
                stage("acquisition", "Data Acquisition", acquisition.count(), acquisition,
                        daysSince(snapshotOldest.get(BillingSnapshotStatus.IN_PROGRESS), asOf), Map.of()),
                stage("tax", "Tax Calculation", taxStage.count(), taxStage, taxOldestDays, orderedMap(
                        "snapshotsReadyForTax", snapshotsReadyForTax.count(),
                        "snapshotsInTax", snapshotsInTax.count(),
                        "schedulesAwaitingTax", schedulesAwaitingTax.count())),
                stage("generation", "Invoice Generation", generationStage.count(), generationStage, oldestGeneratedDays, orderedMap(
                        "snapshotsReadyForInvoice", snapshotsReadyForInvoice.count(),
                        "schedulesReadyForInvoice", schedulesReady.count(),
                        "generatedInvoices", generated.count())),
                stage("invoiceApproval", "Invoice Approval", pendingApproval.count(), pendingApproval,
                        invoiceApprovalOldestDays, Map.of()));

        // --- worklist
        List<String> criticalClients = criticalItems.stream()
                .sorted(Comparator.comparingLong(OverdueItem::daysOverdue).reversed())
                .map(OverdueItem::clientName)
                .filter(Objects::nonNull)
                .distinct()
                .limit(3)
                .toList();
        Long mostOverdue = criticalItems.stream().map(OverdueItem::daysOverdue).max(Comparator.naturalOrder()).orElse(null);

        List<WorklistItemDto> worklist = List.of(
                work("overdue-critical", critical, criticalClients, mostOverdue),
                work("setup-approvals", countOnly(pendingConfigs), List.of(), pendingConfigOldestDays),
                work("tax-ready", taxStage, List.of(), taxOldestDays),
                work("draft-queue", generated, List.of(), oldestGeneratedDays),
                work("awaiting-send", sumRows(repository.sumApprovedNotSent()), List.of(), null),
                work("delivery-failed", countOnly(repository.countFailedDeliveriesNotSent()), List.of(), null));

        // --- kpis
        DashboardKpisDto kpis = DashboardKpisDto.builder()
                .outstanding(outstanding.toMetric())
                .overdue(overdue.toMetric())
                .criticalOverdue(critical.toMetric())
                .criticalOverdueDays(criticalDays)
                .unbilledWip(unbilledWip.toMetric())
                .unbilledProjectCount(unbilledProjects)
                .pendingApproval(pendingApproval.toMetric())
                .invoicedInPeriod(sumRows(repository.sumInvoicedBetween(window.start(), window.end())).toMetric())
                .invoicedPriorPeriod(sumRows(repository.sumInvoicedBetween(window.priorStart(), window.priorEnd())).toMetric())
                .collections(CollectionsDto.builder().tracked(false).reason(COLLECTIONS_NOT_TRACKED).build())
                .build();

        return ArDashboardSummaryDto.builder()
                .asOf(asOf)
                .period(effectivePeriod)
                .periodStart(window.start())
                .periodEnd(window.end())
                .priorPeriodStart(window.priorStart())
                .priorPeriodEnd(window.priorEnd())
                .generatedAt(LocalDateTime.now(clock))
                .kpis(kpis)
                .pipeline(pipeline)
                .trend(buildTrend(asOf))
                .aging(aging.entrySet().stream().map(e -> AgingBucketDto.builder()
                        .key(e.getKey().key)
                        .label(e.getKey().label)
                        .minDays(e.getKey().minDays)
                        .maxDays(e.getKey().maxDays)
                        .count(e.getValue().count())
                        .amounts(e.getValue().amounts())
                        .build()).toList())
                .agingUnknownDateCount(unknownAge)
                .billingModelMix(buildBillingModelMix(window))
                .worklist(worklist)
                .recentActivity(buildActivity())
                .notes(List.of(
                        COLLECTIONS_NOT_TRACKED,
                        "Amounts are totalled per currency; nothing is converted between currencies.",
                        "Outstanding, overdue and aging cover invoices that have been sent to the client.",
                        "Setup approval wait time is measured from the configuration's last update (no submitted-at date is recorded)."))
                .build();
    }

    private List<TrendPointDto> buildTrend(LocalDate asOf) {
        int months = Math.max(1, properties.getTrendMonths());
        YearMonth last = YearMonth.from(asOf);
        YearMonth first = last.minusMonths(months - 1L);

        Map<YearMonth, MoneyTotals> byMonth = new LinkedHashMap<>();
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) byMonth.put(m, new MoneyTotals());
        for (MonthTotalRow row : repository.sumInvoicedByMonth(first.atDay(1), asOf)) {
            if (row.year() == null || row.month() == null) continue;
            MoneyTotals totals = byMonth.get(YearMonth.of(row.year(), row.month()));
            if (totals != null) totals.add(row.currencyCode(), row.amount(), nz(row.count()));
        }
        return byMonth.entrySet().stream().map(e -> TrendPointDto.builder()
                .month(e.getKey().toString())
                .invoiceCount(e.getValue().count())
                .invoiced(e.getValue().amounts())
                .collected(null)
                .build()).toList();
    }

    private List<BillingModelSliceDto> buildBillingModelMix(PeriodWindow window) {
        Map<String, MoneyTotals> byType = new LinkedHashMap<>();
        for (BillingTypeTotalRow row : repository.sumInvoicedByBillingType(window.start(), window.end())) {
            String type = row.billingType() == null || row.billingType().isBlank()
                    ? UNSPECIFIED_BILLING_TYPE : row.billingType().trim();
            byType.computeIfAbsent(type, t -> new MoneyTotals()).add(row.currencyCode(), row.amount(), nz(row.count()));
        }
        return byType.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, MoneyTotals> e) -> e.getValue().count()).reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(e -> BillingModelSliceDto.builder()
                        .billingType(e.getKey())
                        .count(e.getValue().count())
                        .amounts(e.getValue().amounts())
                        .build())
                .toList();
    }

    private List<ActivityEventDto> buildActivity() {
        int limit = Math.max(1, properties.getActivityLimit());
        Stream<ActivityEventDto> approvals = repository.findRecentApprovalEvents(limit).stream().map(this::toActivity);
        Stream<ActivityEventDto> deliveries = repository.findRecentDeliveryEvents(limit).stream().map(this::toActivity);
        Stream<ActivityEventDto> configs = repository.findRecentConfigurationEvents(limit).stream().map(this::toActivity);
        return Stream.of(approvals, deliveries, configs).flatMap(Function.identity())
                .filter(e -> e.getOccurredAt() != null)
                .sorted(Comparator.comparing(ActivityEventDto::getOccurredAt).reversed())
                .limit(limit)
                .toList();
    }

    private ActivityEventDto toActivity(ApprovalEventRow row) {
        String title = switch (row.action() == null ? "" : row.action().name()) {
            case "SUBMITTED" -> "Invoice submitted for approval";
            case "APPROVED" -> "Invoice approved";
            case "REJECTED" -> "Invoice rejected";
            case "CORRECTED" -> "Invoice corrected";
            default -> "Invoice approval updated";
        };
        return ActivityEventDto.builder().type("INVOICE_APPROVAL").title(title)
                .reference(row.invoiceNumber()).actor(row.actionBy()).occurredAt(row.actionAt()).build();
    }

    private ActivityEventDto toActivity(DeliveryEventRow row) {
        String status = row.status() == null ? "" : row.status().name();
        String title = switch (status) {
            case "SENT" -> "Invoice sent to client";
            case "SIMULATED" -> "Invoice delivery simulated (no email sent)"; // local-demo profile only
            case "FAILED" -> "Invoice delivery failed";
            default -> "Invoice delivery updated";
        };
        LocalDateTime at = "FAILED".equals(status)
                ? firstNonNull(row.failedAt(), row.updatedAt())
                : firstNonNull(row.sentAt(), row.updatedAt());
        return ActivityEventDto.builder().type("INVOICE_DELIVERY").title(title)
                .reference(row.invoiceNumber()).occurredAt(at).build();
    }

    private ActivityEventDto toActivity(ConfigEventRow row) {
        String status = row.approvalStatus() == null ? "" : row.approvalStatus().name();
        String title = switch (status) {
            case "DRAFT" -> "Billing setup saved as draft";
            case "PENDING_APPROVAL" -> "Billing setup submitted for approval";
            case "APPROVED" -> "Billing setup approved";
            case "REJECTED" -> "Billing setup rejected";
            default -> "Billing setup updated";
        };
        return ActivityEventDto.builder().type("BILLING_SETUP").title(title)
                .reference(row.projectName()).occurredAt(row.updatedAt()).build();
    }

    // =====================================================================================================  ledger

    @Override
    public List<DashboardInvoiceRowDto> getInvoices(DashboardInvoiceView view, String search, Integer limit, LocalDate asOfOrNull) {
        DashboardInvoiceView effectiveView = view == null ? DashboardInvoiceView.ALL : view;
        LocalDate asOf = asOfOrNull == null ? LocalDate.now(clock) : asOfOrNull;
        int max = Math.max(1, properties.getLedgerMaxLimit());
        int effectiveLimit;
        if (limit == null) {
            effectiveLimit = Math.min(Math.max(1, properties.getLedgerDefaultLimit()), max);
        } else if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1.");
        } else {
            effectiveLimit = Math.min(limit, max);
        }
        String pattern = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";

        return repository.findLedger(effectiveView, pattern, asOf, effectiveLimit).stream()
                .map(row -> toLedgerRow(row, asOf))
                .toList();
    }

    static DashboardInvoiceRowDto toLedgerRow(LedgerRow row, LocalDate asOf) {
        boolean sent = row.receivableId() != null;
        BigDecimal balance = row.outstandingBalance();
        boolean open = sent && balance != null && balance.signum() > 0;
        LocalDate due = row.receivableDueDate() != null ? row.receivableDueDate() : row.dueDate();

        // Same day math as ReceivableServiceImpl, so the ledger and the receivables API agree.
        Long daysOutstanding = open && row.invoiceDate() != null
                ? Math.max(0, ChronoUnit.DAYS.between(row.invoiceDate(), asOf)) : null;
        Long daysOverdue = open && due != null ? Math.max(0, ChronoUnit.DAYS.between(due, asOf)) : null;

        return DashboardInvoiceRowDto.builder()
                .invoiceId(row.invoiceId())
                .invoiceNumber(row.invoiceNumber())
                .clientName(row.clientName())
                .projectName(row.projectName())
                .billingType(row.billingType())
                .invoiceDate(row.invoiceDate())
                .dueDate(due)
                .currencyCode(row.currencyCode())
                .subtotal(row.subtotal())
                .totalTaxAmount(row.totalTaxAmount())
                .grandTotal(row.grandTotal())
                .invoiceStatus(row.status() == null ? null : row.status().name())
                .sent(sent)
                .outstandingBalance(sent ? balance : null)
                .daysOutstanding(daysOutstanding)
                .daysOverdue(daysOverdue)
                .displayStatus(displayStatus(row.status(), sent, open, due, asOf))
                .build();
    }

    static String displayStatus(InvoiceStatus status, boolean sent, boolean open, LocalDate due, LocalDate asOf) {
        if (sent) return open && due != null && due.isBefore(asOf) ? "OVERDUE" : "ISSUED";
        if (status == null) return "GENERATED";
        return switch (status) {
            case APPROVED -> "AWAITING_SEND";
            default -> status.name();
        };
    }

    // =====================================================================================================  helpers

    private record OverdueItem(String clientName, long daysOverdue) {
    }

    private static PipelineStageDto stage(String id, String label, long count, MoneyTotals totals,
                                          Long oldestDays, Map<String, Long> breakdown) {
        return PipelineStageDto.builder()
                .id(id).label(label).count(count).amounts(totals.amounts())
                .oldestWaitingDays(oldestDays).breakdown(breakdown)
                .build();
    }

    private static WorklistItemDto work(String id, MoneyTotals totals, List<String> clients, Long oldestDays) {
        return WorklistItemDto.builder()
                .id(id).count(totals.count()).amounts(totals.amounts())
                .clientNames(clients).oldestDays(oldestDays)
                .build();
    }

    /** A count with no money attached (e.g. configurations, failed deliveries). */
    private static MoneyTotals countOnly(long count) {
        return MoneyTotals.ofCount(count);
    }

    private static MoneyTotals sumRows(Collection<CurrencyTotalRow> rows) {
        MoneyTotals totals = new MoneyTotals();
        for (CurrencyTotalRow row : rows) totals.add(row.currencyCode(), row.amount(), nz(row.count()));
        return totals;
    }

    private static Map<String, Long> orderedMap(String k1, long v1, String k2, long v2, String k3, long v3) {
        Map<String, Long> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return map;
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }

    private static LocalDate toDate(LocalDateTime value) {
        return value == null ? null : value.toLocalDate();
    }

    private static LocalDate earliest(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    private static Long daysSince(LocalDateTime from, LocalDate asOf) {
        return from == null ? null : daysSince(from.toLocalDate(), asOf);
    }

    private static Long daysSince(LocalDate from, LocalDate asOf) {
        return from == null ? null : Math.max(0, ChronoUnit.DAYS.between(from, asOf));
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) if (value != null) return value;
        return null;
    }
}
