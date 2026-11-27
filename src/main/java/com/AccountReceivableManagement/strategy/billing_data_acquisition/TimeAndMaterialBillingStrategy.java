package com.AccountReceivableManagement.strategy.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.AcquisitionScope;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionResultDto;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotCreateRequestDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.TimesheetDto;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTMRateCard;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTMRateDiscount;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingRatePeriod;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.PricingModel;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTMRateDiscountRepository;
import com.AccountReceivableManagement.util.TmRateCalculator;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingRatePeriod;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.PricingModel;
import com.AccountReceivableManagement.integration.billing_data_acquisition.TimesheetIntegration;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTMRateCardRepository;
import com.AccountReceivableManagement.dto.billing_data_acquisition.ExpenseLineDto;
import com.AccountReceivableManagement.integration.billing_data_acquisition.ExpenseIntegration;
import com.AccountReceivableManagement.integration.expense_management.XmsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.UUID;

/**
 * Acquires approved timesheets from TMS for Time & Material billing
 * and retrieves active hourly rates from billing_tm_rate_card for the configuration.
 * <p>
 * Timesheets are billed in hours, so only rate cards priced per hour are
 * used: a DAILY/WEEKLY/MONTHLY card's rate would otherwise be multiplied by
 * hours. No conversion is made (the model has no hours-per-day rule) - a
 * role with only non-hourly cards gets no rate and the acquisition is
 * rejected by {@code BillingAcquisitionValidator}.
 * and resolves each entry's net hourly rate (standard base rate less any dated discount period)
 * from billing_tm_rate_card / billing_tm_rate_discount for the configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeAndMaterialBillingStrategy implements BillingAcquisitionStrategy {

    private final TimesheetIntegration timesheetIntegration;
    private final BillingTMRateCardRepository billingTMRateCardRepository;

    // Optional, field-injected so the two-argument construction used by
    // existing callers/tests keeps working (expenses simply stay off).
    // Optional as well: without it (older two-argument construction) no discount period is applied.
    @Autowired(required = false)
    private BillingTMRateDiscountRepository billingTMRateDiscountRepository;

    @Autowired(required = false)
    private ExpenseIntegration expenseIntegration;

    // Optional for the same reason; incremental acquisition needs it and fails closed without it.
    @Autowired(required = false)
    private com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotItemRepository
            billingSnapshotItemRepository;

    @Autowired(required = false)
    private XmsProperties xmsProperties;

    @Override
    public BillingType getSupportedBillingType() {
        return BillingType.TIME_AND_MATERIAL;
    }

    @Override
    public BillingAcquisitionResultDto acquire(BillingConfigurationResponseDto configuration, BillingSnapshotCreateRequestDto request) {
        return acquire(configuration, request, AcquisitionScope.FAIL_ON_CLAIMED);
    }

    /**
     * Fetches the period's approved timesheets, leaves out those already claimed by another live snapshot
     * when {@code scope} asks for it (before pricing, so a record billed earlier can never block a new
     * batch), prices each remaining timesheet against the rate schedule of its own work date, and acquires
     * the period's expenses when expense billing is on.
     */
    @Override
    public BillingAcquisitionResultDto acquire(BillingConfigurationResponseDto configuration,
                                               BillingSnapshotCreateRequestDto request, AcquisitionScope scope) {
        AcquisitionScope effectiveScope = scope != null ? scope : AcquisitionScope.FAIL_ON_CLAIMED;
        FetchedTimesheets fetched = fetchTimesheets(request.getProjectId(), request.getBillingPeriodStart(),
                request.getBillingPeriodEnd(), effectiveScope);

        UUID configId = request.getBillingConfigurationId() != null
                ? request.getBillingConfigurationId()
                : (configuration != null ? configuration.getBillingConfigurationId() : null);

        applyRates(configuration, configId, fetched.eligible(), request.getBillingPeriodStart());

        return BillingAcquisitionResultDto.builder()
                .billingConfigurationId(configId)
                .timesheets(fetched.eligible())
                .expenses(fetchExpenses(configuration, request.getProjectId(), request.getBillingPeriodStart(),
                        request.getBillingPeriodEnd(), effectiveScope))
                .expensesChecked(configuration != null && configuration.isExpenseEligible())
                .alreadyClaimedTimesheetCount(fetched.alreadyClaimed())
                .build();
    }

    /** Timesheets eligible for acquisition, and how many approved ones were left out as already claimed. */
    public record FetchedTimesheets(List<TimesheetDto> eligible, int alreadyClaimed) {
    }

    /**
     * Approved billable timesheets of the project dated {@code start}..{@code end} (TMS), unpriced. With
     * {@code scope.skipAlreadyClaimed()}, those whose TMS id another live (non-cancelled) Billing Snapshot -
     * other than {@code scope.owningSnapshotId()} - already claims are removed. A timesheet without a TMS id
     * is kept, so validation still rejects it instead of it being billed untracked.
     */
    public FetchedTimesheets fetchTimesheets(Long projectId, LocalDate start, LocalDate end, AcquisitionScope scope) {
        List<TimesheetDto> timesheets = timesheetIntegration.getApprovedTimesheets(projectId, start, end);
        List<TimesheetDto> eligible = timesheets == null ? new ArrayList<>() : new ArrayList<>(timesheets);
        if (scope == null || !scope.skipAlreadyClaimed() || eligible.isEmpty()) {
            return new FetchedTimesheets(eligible, 0);
        }
        if (billingSnapshotItemRepository == null) {
            // Fail closed: without the claim lookup an already-billed timesheet could be billed again.
            throw new IllegalStateException("Incremental acquisition needs the billing snapshot item repository "
                    + "to exclude already-billed timesheets; nothing was acquired.");
        }
        Set<String> ids = new HashSet<>();
        for (TimesheetDto timesheet : eligible) {
            if (timesheet.getSourceReferenceId() != null && !timesheet.getSourceReferenceId().isBlank()) {
                ids.add(timesheet.getSourceReferenceId());
            }
        }
        if (ids.isEmpty()) {
            return new FetchedTimesheets(eligible, 0);
        }
        List<String> claimedList = billingSnapshotItemRepository.findClaimedSourceReferenceIds(
                BillingItemType.TIME_ENTRY, ids, BillingSnapshotStatus.CANCELLED, scope.owningSnapshotId());
        Set<String> claimed = claimedList == null ? Set.of() : new HashSet<>(claimedList);
        if (claimed.isEmpty()) {
            return new FetchedTimesheets(eligible, 0);
        }
        int before = eligible.size();
        eligible.removeIf(timesheet -> claimed.contains(timesheet.getSourceReferenceId()));
        log.info("[TimeAndMaterialAcquisition] projectId={} period={}..{} excluded {} already-claimed timesheet(s); "
                + "{} newly eligible", projectId, start, end, before - eligible.size(), eligible.size());
        return new FetchedTimesheets(eligible, before - eligible.size());
    }

    /**
     * Prices every timesheet against the rate schedule in force on its own work date ({@code fallbackDate}
     * only for a timesheet without one). Throws {@code ValidationException} for an ambiguous or unusable
     * rate schedule; a timesheet with no rate at all is left without one for the validator to reject.
     */
    public void applyRates(BillingConfigurationResponseDto configuration, UUID configId,
                           List<TimesheetDto> timesheets, LocalDate fallbackDate) {
        for (TimesheetDto timesheet : timesheets) {
            LocalDate workDate = timesheet.getWorkDate() != null ? timesheet.getWorkDate() : fallbackDate;
            applyRate(timesheet, configuration, configId, workDate);
        }
    }

    /**
     * Resolves the standard base rate for the work date, applies the discount period containing it (if any)
     * and records the whole calculation on the timesheet. {@code hourlyRate} becomes the net rate.
     *
     * STANDARD (or unspecified) pricing uses only role-less rate cards, falling back to the configuration's
     * hourly rate. ROLE_BASED pricing uses only the rate card whose role matches the timesheet's role - a
     * missing match is an error, never a fallback to another role's (or the role-less) rate.
     */
    private void applyRate(TimesheetDto timesheet, BillingConfigurationResponseDto configuration,
                           UUID configId, LocalDate workDate) {
        boolean roleBased = configuration != null && configuration.getPricingModel() == PricingModel.ROLE_BASED;

        List<BillingTMRateCard> candidates = configId == null ? List.of()
                : billingTMRateCardRepository.findActiveRatesByConfigurationAndDate(configId, workDate);
        if (candidates == null) {
            candidates = List.of();
        }

        BillingTMRateCard card;
        if (roleBased) {
            String role = timesheet.getRole();
            if (role == null || role.isBlank()) {
                throw new GlobalExceptionHandler.ValidationException("Timesheet ["
                        + timesheet.getSourceReferenceId() + "] has no role, which role-based billing requires.");
            }
            List<BillingTMRateCard> matching = candidates.stream()
                    .filter(c -> c.getRoleName() != null && c.getRoleName().trim().equalsIgnoreCase(role.trim()))
                    .toList();
            if (matching.isEmpty()) {
                throw new GlobalExceptionHandler.ValidationException(
                        "No active rate found for role '" + role.trim() + "' on " + workDate
                                + " in billing configuration " + configId + ".");
            }
            if (matching.size() > 1) {
                // Never pick one silently: the save paths reject overlapping same-role cards, so this can only be
                // legacy data, and a guessed rate would put a wrong amount on the invoice.
                throw new GlobalExceptionHandler.ValidationException(
                        "More than one active rate card matches role '" + role.trim() + "' on " + workDate
                                + "; the rate schedule is ambiguous. Correct the overlapping rate cards.");
            }
            card = matching.get(0);
        } else {
            List<BillingTMRateCard> roleLess = candidates.stream()
                    .filter(c -> c.getRoleName() == null || c.getRoleName().isBlank())
                    .toList();
            if (roleLess.size() > 1) {
                // No precedence rule between overlapping Standard cards is defined anywhere (the query's
                // newest-first order was incidental), so a guessed rate could put a wrong amount on the invoice.
                throw new GlobalExceptionHandler.ValidationException(
                        "More than one active Standard rate card applies on " + workDate
                                + "; the rate schedule is ambiguous. Correct the overlapping rate cards.");
            }
            card = roleLess.isEmpty() ? null : roleLess.get(0);
        }

        BigDecimal baseRate;
        if (card != null) {
            if (card.getRatePeriod() != null && card.getRatePeriod() != BillingRatePeriod.HOURLY) {
                throw new GlobalExceptionHandler.ValidationException("The rate for " + workDate
                        + " is a " + card.getRatePeriod() + " rate; timesheet billing needs an HOURLY rate "
                        + "(no billing-unit conversion is defined).");
            }
            baseRate = card.getRate();
        } else if (configuration != null && configuration.getHourlyRate() != null) {
            baseRate = configuration.getHourlyRate();
        } else {
            timesheet.setHourlyRate(null);
            return;
        }

        BillingTMRateDiscount discount = card == null ? null : resolveDiscount(card, workDate);
        TmRateCalculator.RateResult result = discount == null
                ? TmRateCalculator.apply(baseRate, null, null)
                : TmRateCalculator.apply(baseRate, discount.getDiscountType(), discount.getDiscountValue());

        timesheet.setBaseRate(result.baseRate());
        timesheet.setHourlyRate(result.netRate());
        timesheet.setRatePeriod(BillingRatePeriod.HOURLY.name());
        timesheet.setCurrencyCode(configuration != null ? configuration.getCurrencyCode() : null);
        if (discount == null) {
            // Base-rate work: governed by the rate card's own setting, independent of any discount period.
            timesheet.setIncludeInInvoice(card == null
                    || TmRateCalculator.isIncluded(card.getIncludeBaseRateInInvoice()));
        } else {
            timesheet.setDiscountType(discount.getDiscountType().name());
            timesheet.setDiscountValue(discount.getDiscountValue());
            timesheet.setDiscountAmount(result.discountAmount());
            timesheet.setDiscountPeriodId(String.valueOf(discount.getDiscountId()));
            timesheet.setDiscountPeriodName(discount.getPeriodName());
            timesheet.setDiscountPeriodStart(discount.getEffectiveFrom());
            timesheet.setDiscountPeriodEnd(discount.getEffectiveTo());
            timesheet.setIncludeInInvoice(discount.getIncludeInInvoice());
        }
    }

    private BillingTMRateDiscount resolveDiscount(BillingTMRateCard card, LocalDate workDate) {
        if (billingTMRateDiscountRepository == null || card.getRateCardId() == null) {
            return null;
        }
        List<BillingTMRateDiscount> active =
                billingTMRateDiscountRepository.findActiveByRateCardAndDate(card.getRateCardId(), workDate);
        if (active == null || active.isEmpty()) {
            return null;
        }
        if (active.size() > 1) {
            throw new GlobalExceptionHandler.ValidationException(
                    "More than one discount period applies on " + workDate + "; the rate schedule is ambiguous.");
        }
        return active.get(0);
    }

    /**
     * Expenses are an additional source only when the billing configuration
     * is expense-eligible. When it is, XMS must be enabled: a snapshot that
     * silently omitted required expenses would under-bill, so the
     * acquisition fails with an actionable message instead.
     */
    public List<ExpenseLineDto> fetchExpenses(BillingConfigurationResponseDto configuration, Long projectId,
                                              LocalDate start, LocalDate end, AcquisitionScope scope) {
        if (configuration == null || !configuration.isExpenseEligible()) {
            return Collections.emptyList();
        }
        if (expenseIntegration == null || xmsProperties == null || !xmsProperties.isEnabled()) {
            log.error("[ExpenseBilling] projectId={} is expense-eligible but the XMS integration is disabled.",
                    projectId);
            throw new com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler.ValidationException(
                    "Expense billing is enabled for this billing configuration, but the Expense Management (XMS) "
                            + "integration is disabled (xms.api.enabled / XMS_ENABLED). Enable it or turn off expense "
                            + "billing for this configuration; the snapshot was not created.");
        }
        if (scope == null || scope.equals(AcquisitionScope.FAIL_ON_CLAIMED)) {
            return expenseIntegration.getBillableExpenses(projectId, start, end, configuration.getCurrencyCode());
        }
        return expenseIntegration.getBillableExpenses(projectId, start, end, configuration.getCurrencyCode(), scope);
    }
}
