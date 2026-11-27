package com.AccountReceivableManagement.service_Imple.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.AcquisitionScope;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionResultDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingPeriodAcquisitionDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingPeriodDiscoveryResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingPeriodReadiness;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotCreateRequestDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.ExistingBillingBatchDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.ExpenseLineDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.IncrementalBatchRequestDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.NextBillingPeriodResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.TimesheetDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.ValidationResultDto;
import com.AccountReceivableManagement.dto.common.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingPeriodDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshotItem;
import com.AccountReceivableManagement.entity.invoice_generation.Invoice;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.integration.billing_data_acquisition.BillingConfigurationIntegration;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository;
import com.AccountReceivableManagement.service_Imple.projectbilling_config.BillingOccurrenceServiceImpl;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingPeriodAcquisitionService;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingSnapshotService;
import com.AccountReceivableManagement.strategy.billing_data_acquisition.TimeAndMaterialBillingStrategy;
import com.AccountReceivableManagement.util.TmRateCalculator;
import com.AccountReceivableManagement.validator.billing_data_acquisition.BillingAcquisitionValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Incremental, period-wise Time &amp; Material billing.
 *
 * <p>Periods come from the configuration's billing frequency and schedule
 * ({@link BillingOccurrenceServiceImpl#resolveTimeAndMaterialBillingPeriods}) - weekly, monthly or any other
 * configured frequency, partial first and last periods included. Source records are assigned to a period by
 * their own work date (timesheets) or expense date (expenses) - never by submission time or rate-card dates.
 *
 * <p>Whether a record is new is decided per record, by its claim
 * ({@code uk_billing_snapshot_item_billed_source}) - never per period. A period that already has snapshots
 * or invoices therefore still shows, and can acquire, records approved later.
 *
 * <p>Discovery reads TMS once and XMS once for the whole range, not once per period.
 */
@Slf4j
@Service
public class BillingPeriodAcquisitionServiceImpl implements BillingPeriodAcquisitionService {

    private final BillingOccurrenceServiceImpl billingOccurrenceService;
    private final BillingConfigurationIntegration billingConfigurationIntegration;
    private final TimeAndMaterialBillingStrategy timeAndMaterialBillingStrategy;
    private final BillingAcquisitionValidator billingAcquisitionValidator;
    private final BillingSnapshotRepository billingSnapshotRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillingSnapshotService billingSnapshotService;
    private final Clock clock;

    @Autowired
    public BillingPeriodAcquisitionServiceImpl(BillingOccurrenceServiceImpl billingOccurrenceService,
            BillingConfigurationIntegration billingConfigurationIntegration,
            TimeAndMaterialBillingStrategy timeAndMaterialBillingStrategy,
            BillingAcquisitionValidator billingAcquisitionValidator,
            BillingSnapshotRepository billingSnapshotRepository,
            InvoiceRepository invoiceRepository,
            BillingSnapshotService billingSnapshotService) {
        this(billingOccurrenceService, billingConfigurationIntegration, timeAndMaterialBillingStrategy,
                billingAcquisitionValidator, billingSnapshotRepository, invoiceRepository, billingSnapshotService,
                Clock.systemDefaultZone());
    }

    BillingPeriodAcquisitionServiceImpl(BillingOccurrenceServiceImpl billingOccurrenceService,
            BillingConfigurationIntegration billingConfigurationIntegration,
            TimeAndMaterialBillingStrategy timeAndMaterialBillingStrategy,
            BillingAcquisitionValidator billingAcquisitionValidator,
            BillingSnapshotRepository billingSnapshotRepository,
            InvoiceRepository invoiceRepository,
            BillingSnapshotService billingSnapshotService,
            Clock clock) {
        this.billingOccurrenceService = billingOccurrenceService;
        this.billingConfigurationIntegration = billingConfigurationIntegration;
        this.timeAndMaterialBillingStrategy = timeAndMaterialBillingStrategy;
        this.billingAcquisitionValidator = billingAcquisitionValidator;
        this.billingSnapshotRepository = billingSnapshotRepository;
        this.invoiceRepository = invoiceRepository;
        this.billingSnapshotService = billingSnapshotService;
        this.clock = clock;
    }

    // ================================================================ discovery

    @Override
    public BillingPeriodDiscoveryResponseDto discoverPeriods(UUID billingConfigurationId, boolean onlyWithNewItems) {
        LocalDate today = LocalDate.now(clock);
        BillingConfigurationResponseDto configuration = loadApprovedConfiguration(billingConfigurationId);
        List<BillingPeriodDto> periods = startedPeriods(billingConfigurationId, today);

        BillingPeriodDiscoveryResponseDto response = BillingPeriodDiscoveryResponseDto.builder()
                .billingConfigurationId(billingConfigurationId)
                .projectId(configuration.getProjectId())
                .billingFrequency(configuration.getBillingFrequencyName())
                .scheduleStart(periods.isEmpty() ? null : periods.get(0).getPeriodStartDate())
                .scheduleEnd(periods.isEmpty() ? null : periods.get(periods.size() - 1).getPeriodEndDate())
                .expenseBillingEnabled(configuration.isExpenseEligible())
                .asOf(today)
                .build();
        if (periods.isEmpty()) {
            return response;
        }

        LocalDate rangeStart = periods.get(0).getPeriodStartDate();
        LocalDate rangeEnd = periods.get(periods.size() - 1).getPeriodEndDate();
        Long projectId = configuration.getProjectId();

        // ---- newly eligible source records of the whole range, one read per source system
        List<TimesheetDto> timesheets = null;
        String timesheetSourceError = null;
        try {
            timesheets = timeAndMaterialBillingStrategy
                    .fetchTimesheets(projectId, rangeStart, rangeEnd, AcquisitionScope.newBatch()).eligible();
        } catch (RuntimeException ex) {
            timesheetSourceError = "Timesheets could not be read from TMS: " + ex.getMessage();
            log.warn("[BillingPeriodDiscovery] billingConfigurationId={} TMS read failed: {}",
                    billingConfigurationId, ex.getMessage());
        }

        List<ExpenseLineDto> expenses = List.of();
        String expenseSourceError = null;
        if (configuration.isExpenseEligible()) {
            try {
                expenses = timeAndMaterialBillingStrategy.fetchExpenses(configuration, projectId, rangeStart,
                        rangeEnd, AcquisitionScope.newBatch());
            } catch (RuntimeException ex) {
                expenses = null;
                expenseSourceError = "Expenses could not be read from Expense Management (XMS): " + ex.getMessage();
                log.warn("[BillingPeriodDiscovery] billingConfigurationId={} XMS read failed: {}",
                        billingConfigurationId, ex.getMessage());
            }
        }
        if (timesheetSourceError != null) {
            response.getWarnings().add(timesheetSourceError);
        }
        if (expenseSourceError != null) {
            response.getWarnings().add(expenseSourceError);
        }

        // ---- group by the record's own date
        Map<String, List<TimesheetDto>> timesheetsByPeriod = new HashMap<>();
        int undated = 0;
        if (timesheets != null) {
            for (TimesheetDto timesheet : timesheets) {
                BillingPeriodDto period = periodOf(periods, timesheet.getWorkDate());
                if (period == null) {
                    undated++;
                    continue;
                }
                timesheetsByPeriod.computeIfAbsent(key(period), k -> new ArrayList<>()).add(timesheet);
            }
        }
        Map<String, List<ExpenseLineDto>> expensesByPeriod = new HashMap<>();
        if (expenses != null) {
            for (ExpenseLineDto expense : expenses) {
                BillingPeriodDto period = periodOf(periods, expense.getExpenseDate());
                if (period == null) {
                    undated++;
                    continue;
                }
                expensesByPeriod.computeIfAbsent(key(period), k -> new ArrayList<>()).add(expense);
            }
        }
        if (undated > 0) {
            response.getWarnings().add(undated + " source record(s) have no date inside the billing schedule and "
                    + "are not assigned to any billing period.");
        }

        // Batches match a period by exact dates; snapshots of a non-configured period (legacy endpoint, e.g.
        // a calendar month on a weekly schedule) are shown on every period they overlap, display only.
        Set<String> configuredKeys = periods.stream().map(BillingPeriodAcquisitionServiceImpl::key)
                .collect(Collectors.toSet());
        Map<String, List<ExistingBillingBatchDto>> batchesByPeriod = new HashMap<>();
        List<ExistingBillingBatchDto> nonAligned = new ArrayList<>();
        for (ExistingBillingBatchDto batch : existingBatches(billingConfigurationId, configuredKeys)) {
            if (batch.isPeriodAligned()) {
                batchesByPeriod.computeIfAbsent(batch.getBillingPeriodStart() + ".." + batch.getBillingPeriodEnd(),
                        k -> new ArrayList<>()).add(batch);
            } else {
                nonAligned.add(batch);
            }
        }

        for (BillingPeriodDto period : periods) {
            BillingPeriodAcquisitionDto dto = describePeriod(configuration, period, today,
                    timesheets == null ? null : timesheetsByPeriod.getOrDefault(key(period), List.of()),
                    expenses == null ? null : expensesByPeriod.getOrDefault(key(period), List.of()),
                    timesheetSourceError, expenseSourceError,
                    batchesByPeriod.getOrDefault(key(period), List.of()));
            nonAligned.stream()
                    .filter(batch -> batch.getBillingPeriodStart() != null && batch.getBillingPeriodEnd() != null
                            && !batch.getBillingPeriodStart().isAfter(period.getPeriodEndDate())
                            && !batch.getBillingPeriodEnd().isBefore(period.getPeriodStartDate()))
                    .forEach(dto.getOverlappingSnapshots()::add);
            if (!onlyWithNewItems || dto.isHasNewItems()) {
                response.getPeriods().add(dto);
            }
        }
        return response;
    }

    private BillingPeriodAcquisitionDto describePeriod(BillingConfigurationResponseDto configuration,
            BillingPeriodDto period, LocalDate today, List<TimesheetDto> timesheets, List<ExpenseLineDto> expenses,
            String timesheetSourceError, String expenseSourceError, List<ExistingBillingBatchDto> batches) {

        BillingPeriodAcquisitionDto dto = BillingPeriodAcquisitionDto.builder()
                .periodNumber(period.getPeriodNumber())
                .billingPeriodStart(period.getPeriodStartDate())
                .billingPeriodEnd(period.getPeriodEndDate())
                .billingDate(period.getBillingDate())
                .partialPeriod(Boolean.TRUE.equals(period.getIsPartialPeriod()))
                .periodEnded(period.getBillingDate() != null && !period.getBillingDate().isAfter(today))
                .batches(new ArrayList<>(batches))
                .previouslyAcquired(batches.stream()
                        .anyMatch(batch -> batch.getSnapshotStatus() != BillingSnapshotStatus.CANCELLED))
                .build();

        if (timesheets != null) {
            dto.setNewTimesheetCount(timesheets.size());
            dto.setNewBillableHours(timesheets.stream()
                    .map(TimesheetDto::getHours)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        if (!configuration.isExpenseEligible()) {
            dto.setNewExpenseCount(0);
            dto.setNewExpenseAmount(BigDecimal.ZERO);
        } else if (expenses != null) {
            dto.setNewExpenseCount(expenses.size());
            dto.setNewExpenseAmount(expenses.stream()
                    .map(ExpenseLineDto::getCostBasis)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        List<TimesheetDto> knownTimesheets = timesheets != null ? timesheets : List.of();
        List<ExpenseLineDto> knownExpenses = expenses != null ? expenses : List.of();
        dto.setHasNewItems(!knownTimesheets.isEmpty() || !knownExpenses.isEmpty());

        if (timesheetSourceError != null || expenseSourceError != null) {
            dto.setReadiness(BillingPeriodReadiness.SOURCE_UNAVAILABLE);
            if (timesheetSourceError != null) {
                dto.getBlockingReasons().add(timesheetSourceError);
            }
            if (expenseSourceError != null) {
                dto.getBlockingReasons().add(expenseSourceError);
            }
            return dto;
        }
        if (!dto.isHasNewItems()) {
            dto.setReadiness(BillingPeriodReadiness.NO_NEW_ITEMS);
            return dto;
        }

        // ---- the same pricing and validation acquisition will apply, without persisting anything
        try {
            timeAndMaterialBillingStrategy.applyRates(configuration, configuration.getBillingConfigurationId(),
                    knownTimesheets, period.getPeriodStartDate());
        } catch (GlobalExceptionHandler.ValidationException ex) {
            dto.setRateValidationPassed(Boolean.FALSE);
            dto.setReadiness(BillingPeriodReadiness.BLOCKED);
            dto.getBlockingReasons().add(ex.getMessage());
            return dto;
        }

        List<TimesheetDto> unpriced = knownTimesheets.stream()
                .filter(timesheet -> timesheet.getHourlyRate() == null
                        || timesheet.getHourlyRate().compareTo(BigDecimal.ZERO) <= 0)
                .toList();
        dto.setRateValidationPassed(knownTimesheets.isEmpty() ? null : unpriced.isEmpty());
        for (TimesheetDto timesheet : unpriced) {
            dto.getBlockingReasons().add("No active hourly rate for timesheet [" + timesheet.getSourceReferenceId()
                    + "] on " + timesheet.getWorkDate()
                    + (timesheet.getRole() != null && !timesheet.getRole().isBlank()
                    ? " (role " + timesheet.getRole().trim() + ")" : "") + ".");
        }
        dto.setEstimatedTimesheetAmount(knownTimesheets.stream()
                .filter(timesheet -> timesheet.getHourlyRate() != null && timesheet.getHours() != null)
                .filter(timesheet -> TmRateCalculator.isIncluded(timesheet.getIncludeInInvoice()))
                .map(timesheet -> TmRateCalculator.lineAmount(timesheet.getHours(), timesheet.getHourlyRate()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        if (unpriced.isEmpty()) {
            ValidationResultDto validation = billingAcquisitionValidator.validate(BillingAcquisitionResultDto.builder()
                    .billingConfigurationId(configuration.getBillingConfigurationId())
                    .timesheets(knownTimesheets)
                    .expenses(knownExpenses)
                    .expensesChecked(configuration.isExpenseEligible())
                    .build());
            if (!validation.isSuccess()) {
                dto.getBlockingReasons().add(validation.getValidationMessage());
            }
        }
        dto.setReadiness(dto.getBlockingReasons().isEmpty()
                ? BillingPeriodReadiness.READY : BillingPeriodReadiness.BLOCKED);
        return dto;
    }

    /**
     * This configuration's batches, each with its own period, its invoice, and whether that period is one of
     * {@code configuredPeriodKeys}. Read-only.
     */
    private List<ExistingBillingBatchDto> existingBatches(UUID billingConfigurationId,
            Set<String> configuredPeriodKeys) {
        List<BillingSnapshot> snapshots = billingSnapshotRepository
                .findAllByBillingConfigurationIdOrderByBillingPeriodStartDesc(billingConfigurationId);
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        Map<UUID, Invoice> invoicesBySnapshot = new HashMap<>();
        List<Invoice> invoices = invoiceRepository.findAllByBillingSnapshotIdIn(
                snapshots.stream().map(BillingSnapshot::getId).toList());
        if (invoices != null) {
            invoices.forEach(invoice -> invoicesBySnapshot.put(invoice.getBillingSnapshotId(), invoice));
        }

        List<ExistingBillingBatchDto> batches = new ArrayList<>();
        snapshots.stream()
                .sorted(Comparator.comparing((BillingSnapshot s) -> s.getBatchSequence() != null ? s.getBatchSequence() : 1)
                        .thenComparing(BillingSnapshot::getCreatedDate, Comparator.nullsFirst(Comparator.naturalOrder())))
                .forEach(snapshot -> {
                    Invoice invoice = invoicesBySnapshot.get(snapshot.getId());
                    List<BillingSnapshotItem> items = snapshot.getItems() != null ? snapshot.getItems() : List.of();
                    batches.add(ExistingBillingBatchDto.builder()
                            .snapshotId(snapshot.getId())
                            .billingPeriodStart(snapshot.getBillingPeriodStart())
                            .billingPeriodEnd(snapshot.getBillingPeriodEnd())
                            .periodAligned(configuredPeriodKeys.contains(
                                    snapshot.getBillingPeriodStart() + ".." + snapshot.getBillingPeriodEnd()))
                            .snapshotNumber(snapshot.getSnapshotNumber())
                            .batchSequence(snapshot.getBatchSequence() != null ? snapshot.getBatchSequence() : 1)
                            .snapshotStatus(snapshot.getStatus())
                            .totalAmount(snapshot.getTotalAmount())
                            .timesheetCount((int) items.stream()
                                    .filter(item -> item.getItemType() == BillingItemType.TIME_ENTRY).count())
                            .expenseCount((int) items.stream()
                                    .filter(item -> item.getItemType() == BillingItemType.EXPENSE).count())
                            .createdDate(snapshot.getCreatedDate())
                            .invoiceId(invoice != null ? invoice.getInvoiceId() : null)
                            .invoiceNumber(invoice != null ? invoice.getInvoiceNumber() : null)
                            .invoiceStatus(invoice != null && invoice.getStatus() != null
                                    ? invoice.getStatus().name() : null)
                            .build());
                });
        return batches;
    }

    // ================================================================ next period

    @Override
    public NextBillingPeriodResponseDto nextPeriod(UUID billingConfigurationId) {
        // Same configuration lookup, approval/project checks and schedule resolution as GET .../periods.
        BillingPeriodDiscoveryResponseDto discovery = discoverPeriods(billingConfigurationId, false);
        List<BillingPeriodAcquisitionDto> periods = discovery.getPeriods();

        NextBillingPeriodResponseDto.NextBillingPeriodResponseDtoBuilder result = NextBillingPeriodResponseDto.builder()
                .projectId(discovery.getProjectId())
                .billingConfigurationId(billingConfigurationId)
                .billingFrequency(discovery.getBillingFrequency())
                .effectiveFrom(discovery.getScheduleStart());

        if (periods.isEmpty()) {
            return result.reason("No billing period of this Billing Configuration has started yet.").build();
        }
        // Earliest READY period: a blocked earlier period never holds back a later one that can be acquired
        // (it is reported in warnings instead). With none READY, the earliest period with unbilled work, so its
        // blocking reason is shown; otherwise the most recent started period. A TMS/XMS outage leaves "new work"
        // unknown, so the latest period is offered with that reason.
        BillingPeriodAcquisitionDto latest = periods.get(periods.size() - 1);
        BillingPeriodAcquisitionDto next = periods.stream()
                .filter(period -> period.getReadiness() == BillingPeriodReadiness.READY)
                .findFirst()
                .orElseGet(() -> periods.stream().filter(BillingPeriodAcquisitionDto::isHasNewItems)
                        .findFirst().orElse(latest));
        List<String> warnings = new ArrayList<>();
        for (BillingPeriodAcquisitionDto earlier : periods) {
            if (!earlier.getBillingPeriodStart().isBefore(next.getBillingPeriodStart())) {
                break;
            }
            if (earlier.isHasNewItems() && earlier.getReadiness() != BillingPeriodReadiness.READY) {
                warnings.add("Billing period " + earlier.getBillingPeriodStart() + " to "
                        + earlier.getBillingPeriodEnd() + " has unbilled records but cannot be acquired: "
                        + String.join(" ", earlier.getBlockingReasons()));
            }
        }
        result.warnings(warnings);
        if (!next.isHasNewItems() && next.isPreviouslyAcquired() && next.getBlockingReasons().isEmpty()) {
            return result.reason("Every billing period of this Billing Configuration has already been acquired.")
                    .build();
        }

        String period = next.getBillingPeriodStart() + " to " + next.getBillingPeriodEnd();
        boolean ready = next.getReadiness() == BillingPeriodReadiness.READY;
        String reason;
        if (!next.getBlockingReasons().isEmpty()) {
            reason = String.join(" ", next.getBlockingReasons());
        } else if (!next.isPeriodEnded()) {
            reason = "The next billing period " + period + " ends on " + next.getBillingDate()
                    + " and can be acquired once it has ended.";
        } else if (ready) {
            reason = "Billing period " + period + " has ended and has " + next.getNewTimesheetCount()
                    + " approved billable timesheet(s) (" + next.getNewBillableHours() + " hours)"
                    + (next.getNewExpenseCount() != null && next.getNewExpenseCount() > 0
                    ? " and " + next.getNewExpenseCount() + " expense(s)" : "") + " not yet billed.";
        } else {
            reason = "Billing period " + period + " has no approved billable records to acquire.";
        }

        // Most recent live batch of an earlier period.
        ExistingBillingBatchDto previousBatch = null;
        BillingPeriodAcquisitionDto previousPeriod = null;
        for (BillingPeriodAcquisitionDto candidate : periods) {
            if (!candidate.getBillingPeriodStart().isBefore(next.getBillingPeriodStart())) {
                break;
            }
            for (ExistingBillingBatchDto batch : candidate.getBatches()) {
                if (batch.getSnapshotStatus() != BillingSnapshotStatus.CANCELLED) {
                    previousBatch = batch;
                    previousPeriod = candidate;
                }
            }
        }

        return result
                .billingPeriodStart(next.getBillingPeriodStart())
                .billingPeriodEnd(next.getBillingPeriodEnd())
                .partialPeriod(next.isPartialPeriod())
                .periodEnded(next.isPeriodEnded())
                .acquisitionAllowed(ready && next.isPeriodEnded())
                .alreadyInvoiced(next.getBatches().stream().anyMatch(batch ->
                        batch.getSnapshotStatus() != BillingSnapshotStatus.CANCELLED && batch.getInvoiceId() != null))
                .eligibleRecordCount(next.getNewTimesheetCount())
                .eligibleHours(next.getNewBillableHours())
                .previousSnapshotId(previousBatch != null ? previousBatch.getSnapshotId() : null)
                .previousPeriodStart(previousPeriod != null ? previousPeriod.getBillingPeriodStart() : null)
                .previousPeriodEnd(previousPeriod != null ? previousPeriod.getBillingPeriodEnd() : null)
                .previousSnapshotStatus(previousBatch != null && previousBatch.getSnapshotStatus() != null
                        ? previousBatch.getSnapshotStatus().name() : null)
                .previousPeriodInvoiced(previousBatch != null && previousBatch.getInvoiceId() != null)
                .reason(reason)
                .build();
    }

    // ================================================================ acquisition

    @Override
    public ApiResponse<BillingSnapshotResponseDto> acquireBatch(UUID billingConfigurationId,
            IncrementalBatchRequestDto request) {
        if (request == null || request.getBillingPeriodStart() == null || request.getBillingPeriodEnd() == null) {
            throw new GlobalExceptionHandler.ValidationException("Billing period start and end are required.");
        }
        BillingConfigurationResponseDto configuration = loadApprovedConfiguration(billingConfigurationId);
        LocalDate today = LocalDate.now(clock);

        // Exact boundaries of one of the configuration's own periods: billing frequency and schedule decide
        // how records are grouped - an arbitrary date range would split or merge periods.
        boolean isConfiguredPeriod = startedPeriods(billingConfigurationId, today).stream()
                .anyMatch(period -> period.getPeriodStartDate().equals(request.getBillingPeriodStart())
                        && period.getPeriodEndDate().equals(request.getBillingPeriodEnd()));
        if (!isConfiguredPeriod) {
            throw new GlobalExceptionHandler.ValidationException("Billing period " + request.getBillingPeriodStart()
                    + " to " + request.getBillingPeriodEnd() + " is not one of this configuration's billing periods "
                    + "(or has not started yet). Use a period returned by period discovery.");
        }

        return billingSnapshotService.createIncrementalBatch(BillingSnapshotCreateRequestDto.builder()
                        .projectId(configuration.getProjectId())
                        .billingConfigurationId(billingConfigurationId)
                        .billingPeriodStart(request.getBillingPeriodStart())
                        .billingPeriodEnd(request.getBillingPeriodEnd())
                        .build(),
                request.getIdempotencyKey());
    }

    // ================================================================ helpers

    private BillingConfigurationResponseDto loadApprovedConfiguration(UUID billingConfigurationId) {
        BillingConfigurationResponseDto configuration =
                billingConfigurationIntegration.getApprovedBillingConfigurationById(billingConfigurationId);
        if (configuration == null || !configuration.isApproved()) {
            throw new GlobalExceptionHandler.ValidationException("Approved Billing Configuration not found.");
        }
        if (configuration.getProjectId() == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Configuration " + billingConfigurationId + " has no project.");
        }
        return configuration;
    }

    /**
     * The configuration's billing periods that have started by {@code today}: a schedule with a fixed end
     * resolves every period up to it, but no work can be recorded in a period that has not begun. Resolving
     * validates Time &amp; Material, approval, Effective From and frequency, and throws otherwise.
     */
    private List<BillingPeriodDto> startedPeriods(UUID billingConfigurationId, LocalDate today) {
        return billingOccurrenceService.resolveTimeAndMaterialBillingPeriods(billingConfigurationId, today).stream()
                .filter(period -> !period.getPeriodStartDate().isAfter(today))
                .toList();
    }

    private static BillingPeriodDto periodOf(List<BillingPeriodDto> periods, LocalDate date) {
        if (date == null) {
            return null;
        }
        for (BillingPeriodDto period : periods) {
            if (!date.isBefore(period.getPeriodStartDate()) && !date.isAfter(period.getPeriodEndDate())) {
                return period;
            }
        }
        return null;
    }

    private static String key(BillingPeriodDto period) {
        return period.getPeriodStartDate() + ".." + period.getPeriodEndDate();
    }
}
