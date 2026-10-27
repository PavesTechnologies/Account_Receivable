package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingMilestonePlanRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingMilestonePlanResponseDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingPaymentEntryRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingPaymentEntryResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingMilestonePlan;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingPaymentEntry;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.project_entity.ProjectMilestoneReference;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.PaymentStructure;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.project.ProjectMilestoneReferenceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingPaymentEntryRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingMilestonePlanRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingMilestonePlanService;
import com.AccountReceivableManagement.entity_enums.common.LockResourceType;
import com.AccountReceivableManagement.service_interface.concurrency_approval.RecordActionLockService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class BillingMilestonePlanServiceImpl implements BillingMilestonePlanService {

    private final BillingMilestonePlanRepository billingMilestonePlanRepository;
    private final BillingPaymentEntryRepository billingPaymentEntryRepository;
    private final BillingConfigurationRepository billingConfigurationRepository;

    /** The PMS milestones synchronised by CDC; read (and row-locked) to validate MILESTONES plans. Never written here. */
    private final ProjectMilestoneReferenceRepository projectMilestoneReferenceRepository;
    private final BillingOccurrenceServiceImpl billingOccurrenceService;
    private final BillingConfigurationChangeTrackingService changeTrackingService;
    private final RecordActionLockService recordActionLockService;

    /** Locked by update/delete to read the occurrences' current status; see {@link #lockOccurrencesAndFindFrozenEntries}. */
    private final BillingScheduleRepository billingScheduleRepository;

    /**
     * Occurrence states from which a milestone entry has left the plan's control. An occurrence in any of them (or one that
     * is flagged invoiced) means billing for that entry has started, so the entry is frozen and its milestone is billed.
     * SCHEDULED is deliberately absent: it is only a reservation - the occurrence is deleted and regenerated whenever the
     * plan is re-approved - and the milestone can still be changed or released. An invoice's own status (GENERATED,
     * PENDING_APPROVAL, APPROVED, REJECTED) is deliberately not consulted: the occurrence is flagged invoiced the moment an
     * invoice exists, and no code path ever releases it (a REJECTED invoice is corrected and resubmitted in place), so the
     * invoice status cannot change whether the milestone is billed.
     */
    public static final List<BillingPeriodStatus> IN_BILLING_STATUSES = List.of(
            BillingPeriodStatus.TAX_PENDING, BillingPeriodStatus.TAX_CALCULATED, BillingPeriodStatus.INVOICED);

    /** PMS MilestoneBillingReadiness value that marks a milestone as billable now. */
    static final String READY_FOR_BILLING = "READY_FOR_BILLING";

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Override
    @Transactional
    public BillingMilestonePlanResponseDto create(
            UUID billingConfigurationId,
            BillingMilestonePlanRequestDto request) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(billingConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Billing Configuration not found."
                                ));

        // Block if another user holds the parent BILLING_CONFIGURATION edit lock
        recordActionLockService.validateNotLockedByAnotherUser(
                LockResourceType.BILLING_CONFIGURATION,
                billingConfigurationId
        );

        validateBillingConfigurationForMilestonePlan(configuration);

        if (billingMilestonePlanRepository
                .existsByBillingConfigurationAndIsActiveTrue(configuration)) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Milestone Plan configuration already exists."
            );
        }

        validateRequest(request);

        validateMilestoneReferences(configuration, request, Set.of());

        BillingMilestonePlan milestonePlan =
                BillingMilestonePlan.builder()
                        .billingConfiguration(configuration)
                        .totalContractValue(request.getTotalContractValue())
                        .paymentStructure(request.getPaymentStructure())
                        .remarks(request.getRemarks())
                        .isActive(true)
                        .build();

        BillingMilestonePlan savedPlan = billingMilestonePlanRepository.save(milestonePlan);

        List<BillingPaymentEntry> entries = createEntries(
                savedPlan,
                request.getPaymentStructure(),
                request.getTotalContractValue(),
                request.getEntries(),
                Map.of()
        );

        billingPaymentEntryRepository.saveAll(entries);

        // Update parent BillingConfiguration with the actual contract value
        configuration.setContractValue(request.getTotalContractValue());
        configuration.setUpdatedAt(LocalDateTime.now());
        billingConfigurationRepository.save(configuration);

        // a plan that has just been created has no occurrences yet
        return mapToResponse(savedPlan, entries, false);
    }

    @Override
    @Transactional
    public BillingMilestonePlanResponseDto update(
            UUID milestonePlanId,
            BillingMilestonePlanRequestDto request) {

        BillingMilestonePlan milestonePlan =
                billingMilestonePlanRepository.findById(milestonePlanId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Milestone Plan Configuration not found."
                                ));

        BillingConfiguration configuration =
                milestonePlan.getBillingConfiguration();

        ApprovalStatus originalApprovalStatus = configuration.getApprovalStatus();

        // Block if another user holds the parent BILLING_CONFIGURATION edit lock
        recordActionLockService.validateNotLockedByAnotherUser(
                LockResourceType.BILLING_CONFIGURATION,
                configuration.getBillingConfigurationId()
        );

        validateBillingConfigurationForMilestonePlan(configuration);
        rejectEditOfExpiredMilestonePlan(configuration, milestonePlan.getPaymentStructure() == PaymentStructure.MILESTONES
                ? PaymentStructure.MILESTONES : request.getPaymentStructure());

        validateRequest(request);

        // Entries whose billing has already started are frozen: they must come back unchanged, and the contract value they
        // were priced against must not move. Only the rest of the plan can be edited (see freezeEntriesInBilling).
        Map<Long, BillingPaymentEntry> frozen = freezeEntriesInBilling(milestonePlan, request);

        validateMilestoneReferences(configuration, request, frozen.keySet());

        // Validate billing dates against project dates
        validateBillingDatesAgainstProject(configuration, request);

        // =========================================================
        // SNAPSHOT CREATION FOR APPROVED+ACTIVE EDIT
        // =========================================================
        com.AccountReceivableManagement.entity.projectbilling_config.BillingConfigurationSnapshot snapshot = null;
        List<BillingPaymentEntry> previousEntries = null;

        if (originalApprovalStatus == ApprovalStatus.APPROVED &&
            configuration.getBillingStatus() == BillingConfigurationStatus.ACTIVE) {
            log.info("Editing APPROVED+ACTIVE Milestone Plan: {}", milestonePlanId);

            // Capture previous entries for comparison
            previousEntries = billingPaymentEntryRepository
                    .findByMilestonePlanAndIsActiveTrueOrderBySequenceAsc(milestonePlan);

            // Create snapshot of the approved state
            snapshot = changeTrackingService.createSnapshot(configuration);
            log.info("Created snapshot {} for Milestone Plan configuration {}", snapshot.getSnapshotId(), configuration.getBillingConfigurationId());
        }

        // Deactivate the existing entries - except the frozen ones, which keep their identity, amount and occurrence
        List<BillingPaymentEntry> existingEntries =
                billingPaymentEntryRepository.findByMilestonePlanAndIsActiveTrue(milestonePlan).stream()
                        .filter(entry -> !frozen.containsKey(entry.getPmsMilestoneId()))
                        .collect(Collectors.toList());
        existingEntries.forEach(entry -> entry.setIsActive(false));
        billingPaymentEntryRepository.saveAll(existingEntries);

        // Update plan
        milestonePlan.setTotalContractValue(request.getTotalContractValue());
        milestonePlan.setPaymentStructure(request.getPaymentStructure());
        milestonePlan.setRemarks(request.getRemarks());

        BillingMilestonePlan savedPlan = billingMilestonePlanRepository.save(milestonePlan);

        // Create new entries (for the frozen ones nothing is created)
        List<BillingPaymentEntry> createdEntries = createEntries(
                savedPlan,
                request.getPaymentStructure(),
                request.getTotalContractValue(),
                request.getEntries(),
                frozen
        );

        billingPaymentEntryRepository.saveAll(createdEntries);

        // The plan's entries after the edit: the frozen ones, untouched, plus the newly created ones.
        List<BillingPaymentEntry> newEntries = new ArrayList<>(frozen.values());
        newEntries.addAll(createdEntries);
        newEntries.sort(Comparator.comparing(BillingPaymentEntry::getSequence));

        // =========================================================
        // TRACK CHANGES FOR APPROVED+ACTIVE EDIT
        // =========================================================
        if (snapshot != null && previousEntries != null) {
            changeTrackingService.trackMilestonePlanChanges(snapshot, previousEntries, newEntries);
            log.info("Tracked milestone plan changes for snapshot {}", snapshot.getSnapshotId());
        }

        // Update parent BillingConfiguration with the actual contract value
        configuration.setContractValue(request.getTotalContractValue());

        // Handle approval state transition
        // Handle approval state transition
        handleApprovalStateTransition(configuration);

        configuration.setUpdatedAt(LocalDateTime.now());
        billingConfigurationRepository.save(configuration);

        // NOTE: Occurrence reconciliation is NOT performed here.
        // Reconciliation only happens after Finance APPROVES the pending changes
        // in the BillingConfigurationServiceImpl.approve() method.

        return mapToResponse(savedPlan, newEntries);
    }

    @Override
    @Transactional(readOnly = true)
    public BillingMilestonePlanResponseDto get(UUID milestonePlanId) {
        BillingMilestonePlan milestonePlan =
                billingMilestonePlanRepository.findById(milestonePlanId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Milestone Plan Configuration not found."
                                ));

        List<BillingPaymentEntry> entries =
                billingPaymentEntryRepository.findByMilestonePlanAndIsActiveTrueOrderBySequenceAsc(milestonePlan);

        return mapToResponse(milestonePlan, entries);
    }

    @Override
    @Transactional(readOnly = true)
    public BillingMilestonePlanResponseDto getByBillingConfiguration(UUID billingConfigurationId) {
        BillingConfiguration configuration =
                billingConfigurationRepository.findById(billingConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Billing Configuration not found."
                                ));

        return findActiveByBillingConfiguration(configuration)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException(
                                "Milestone Plan Configuration not found for this billing configuration."
                        ));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<BillingMilestonePlanResponseDto> findActiveByBillingConfiguration(
            BillingConfiguration configuration) {

        return billingMilestonePlanRepository.findByBillingConfigurationAndIsActiveTrue(configuration)
                .map(milestonePlan -> mapToResponse(
                        milestonePlan,
                        billingPaymentEntryRepository
                                .findByMilestonePlanAndIsActiveTrueOrderBySequenceAsc(milestonePlan)));
    }

    @Override
    @Transactional
    public void delete(UUID milestonePlanId) {
        BillingMilestonePlan milestonePlan =
                billingMilestonePlanRepository.findById(milestonePlanId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Milestone Plan Configuration not found."
                                ));

        BillingConfiguration configuration =
                milestonePlan.getBillingConfiguration();

        // Block if another user holds the parent BILLING_CONFIGURATION edit lock
        recordActionLockService.validateNotLockedByAnotherUser(
                LockResourceType.BILLING_CONFIGURATION,
                configuration.getBillingConfigurationId()
        );

        validateBillingConfigurationForMilestonePlan(configuration);
        rejectEditOfExpiredMilestonePlan(configuration, milestonePlan.getPaymentStructure());

        // Deactivating the plan would release every milestone it holds, including the ones already being billed or invoiced.
        if (milestonePlan.getPaymentStructure() == PaymentStructure.MILESTONES) {
            Collection<BillingPaymentEntry> inBilling = lockOccurrencesAndFindFrozenEntries(milestonePlan).values();
            if (!inBilling.isEmpty()) {
                throw new GlobalExceptionHandler.ValidationException(
                        "This Milestone Plan cannot be deleted: billing has already started for PMS milestone "
                                + inBilling.iterator().next().getPmsMilestoneId() + " (" + inBilling.size()
                                + " milestone entr" + (inBilling.size() == 1 ? "y" : "ies") + " in billing or invoiced).");
            }
        }

        milestonePlan.setIsActive(false);

        // Deactivate all entries
        List<BillingPaymentEntry> entries =
                billingPaymentEntryRepository.findByMilestonePlanAndIsActiveTrue(milestonePlan);
        entries.forEach(entry -> entry.setIsActive(false));
        billingPaymentEntryRepository.saveAll(entries);

        billingMilestonePlanRepository.save(milestonePlan);
    }

    /**
     * An EXPIRED configuration is finished: its milestones are released to other configurations (the claim queries exclude
     * it) and the scheduler no longer progresses its occurrences, and a configuration can only be edited while DRAFT or
     * APPROVED+ACTIVE (BillingConfigurationServiceImpl.update). Editing an expired MILESTONES plan would send the APPROVED
     * configuration back to PENDING_APPROVAL, i.e. revive it and re-claim milestones another configuration may hold now. Only
     * MILESTONES plans are guarded; the other payment structures keep their existing behaviour.
     */
    private void rejectEditOfExpiredMilestonePlan(BillingConfiguration configuration, PaymentStructure structure) {
        if (structure == PaymentStructure.MILESTONES && configuration.getBillingStatus() == BillingConfigurationStatus.EXPIRED) {
            throw new GlobalExceptionHandler.ValidationException(
                    "An expired billing configuration cannot be edited or deleted: its milestones have been released.");
        }
    }

    private void validateBillingConfigurationForMilestonePlan(BillingConfiguration configuration) {
        if (configuration.getBillingType() == null ||
                configuration.getBillingType().getBillingTypeName() == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Type is required for Milestone Plan configuration.");
        }

        String billingTypeName = configuration.getBillingType().getBillingTypeName().trim();

        if (!billingTypeName.equalsIgnoreCase("Milestone Plan")) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Milestone Plan configuration can only be created for Milestone Plan billing type. " +
                    "Current type: " + billingTypeName);
        }
    }

    private void validateRequest(BillingMilestonePlanRequestDto request) {
        if (request.getTotalContractValue() == null ||
                request.getTotalContractValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Total Contract Value must be greater than zero.");
        }

        if (request.getPaymentStructure() == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Payment Structure is required.");
        }

        validateEntriesForPaymentStructure(
                request.getPaymentStructure(),
                request.getEntries(),
                request.getTotalContractValue()
        );
    }

    private void validateEntriesForPaymentStructure(
            PaymentStructure paymentStructure,
            List<BillingPaymentEntryRequestDto> entries,
            BigDecimal totalContractValue) {

        if (entries == null || entries.isEmpty()) {
            throw new GlobalExceptionHandler.ValidationException(
                    "At least one payment entry is required.");
        }

        switch (paymentStructure) {
            case FULL_PAYMENT:
                validateFullPaymentEntries(entries, totalContractValue);
                break;
            case INSTALLMENTS:
                validateInstallmentEntries(entries, totalContractValue);
                break;
            case MILESTONES:
                validateMilestoneEntries(entries);
                break;
        }
    }

    private void validateBillingDatesAgainstProject(
            BillingConfiguration configuration,
            BillingMilestonePlanRequestDto request) {

        // Only validate if the configuration has a project
        if (configuration.getProject() == null) {
            return;
        }

        java.time.LocalDate projectStart = configuration.getProject().getStartDate();
        java.time.LocalDate projectEnd = configuration.getProject().getEndDate();

        // If project dates are not available, skip validation
        if (projectStart == null || projectEnd == null) {
            return;
        }

        // Validate each billing date in the request
        if (request.getEntries() != null) {
            for (BillingPaymentEntryRequestDto entry : request.getEntries()) {
                if (entry.getBillingDate() != null) {
                    // Validate billing date is not before project start
                    if (entry.getBillingDate().isBefore(projectStart)) {
                        throw new GlobalExceptionHandler.ValidationException(
                                "Billing date cannot be before Project Start Date (" + projectStart + ").");
                    }

                    // Validate billing date is not after project end
                    if (entry.getBillingDate().isAfter(projectEnd)) {
                        throw new GlobalExceptionHandler.ValidationException(
                                "Billing date cannot be after Project End Date (" + projectEnd + ").");
                    }
                }
            }
        }
    }

    private void validateFullPaymentEntries(
            List<BillingPaymentEntryRequestDto> entries,
            BigDecimal totalContractValue) {

        if (entries.size() != 1) {
            throw new GlobalExceptionHandler.ValidationException(
                    "FULL_PAYMENT requires exactly one entry with 100% percentage.");
        }

        BillingPaymentEntryRequestDto entry = entries.get(0);

        if (entry.getPercentage() == null ||
                entry.getPercentage().compareTo(new BigDecimal("100")) != 0) {
            throw new GlobalExceptionHandler.ValidationException(
                    "FULL_PAYMENT entry must have exactly 100% percentage.");
        }

        if (entry.getBillingDate() == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing date is required for FULL_PAYMENT entry.");
        }
    }

    private void validateInstallmentEntries(
            List<BillingPaymentEntryRequestDto> entries,
            BigDecimal totalContractValue) {

        BigDecimal totalPercentage = BigDecimal.ZERO;

        for (BillingPaymentEntryRequestDto entry : entries) {
            if (entry.getSequence() == null || entry.getSequence() <= 0) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Sequence must be greater than zero for each installment.");
            }

            if (entry.getPercentage() == null ||
                    entry.getPercentage().compareTo(BigDecimal.ZERO) <= 0) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Each installment must have a percentage greater than zero.");
            }

            if (entry.getPercentage().compareTo(new BigDecimal("100")) > 0) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Installment percentage cannot exceed 100%.");
            }

            if (entry.getBillingDate() == null) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Billing date is required for each installment.");
            }

            totalPercentage = totalPercentage.add(entry.getPercentage());
        }

        if (totalPercentage.compareTo(new BigDecimal("100")) != 0) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Total installment percentage must equal exactly 100%. Current total: " +
                    totalPercentage + "%");
        }

        // Check for duplicate sequences
        long uniqueSequences = entries.stream()
                .map(BillingPaymentEntryRequestDto::getSequence)
                .distinct()
                .count();

        if (uniqueSequences != entries.size()) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Installment sequences must be unique.");
        }
    }

    /**
     * Structural rules of a MILESTONES plan (no database access): every entry names a PMS milestone, no milestone is
     * repeated, sequences are positive and unique, each percentage is in (0, 100] with at most two decimals (the column's
     * scale - more would be silently rounded), and every entry has a billing date, no two entries on the same one (the
     * billing occurrences generated from the plan need exactly one date per entry, one occurrence per date).
     *
     * The percentages are shares of the plan's total contract value and need NOT add up to 100: a plan may bill only some
     * of the contract, the rest being added by later edits of the plan as further milestones become eligible. What is enforced
     * instead is that they never add up to MORE than 100. Whether the milestones themselves are valid and billable is
     * checked by {@link #validateMilestoneReferences}; the amounts against the contract value by
     * {@link #createMilestoneEntries}.
     */
    private void validateMilestoneEntries(List<BillingPaymentEntryRequestDto> entries) {
        Set<Long> milestoneIds = new HashSet<>();
        Set<Integer> sequences = new HashSet<>();
        Set<java.time.LocalDate> billingDates = new HashSet<>();
        BigDecimal totalPercentage = BigDecimal.ZERO;

        for (BillingPaymentEntryRequestDto entry : entries) {
            if (entry.getPmsMilestoneId() == null || entry.getPmsMilestoneId() <= 0) {
                throw new GlobalExceptionHandler.ValidationException(
                        "A PMS milestone must be selected for every payment entry of a MILESTONES plan.");
            }
            if (!milestoneIds.add(entry.getPmsMilestoneId())) {
                throw new GlobalExceptionHandler.ValidationException(
                        "PMS milestone " + entry.getPmsMilestoneId() + " is selected more than once in this plan.");
            }
            if (entry.getSequence() == null || entry.getSequence() <= 0) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Sequence must be greater than zero for each milestone payment entry.");
            }
            if (!sequences.add(entry.getSequence())) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Milestone payment entry sequences must be unique.");
            }
            if (entry.getPercentage() == null ||
                    entry.getPercentage().compareTo(BigDecimal.ZERO) <= 0) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Each milestone must have a percentage greater than zero.");
            }
            if (entry.getPercentage().compareTo(HUNDRED) > 0) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Milestone percentage cannot exceed 100%.");
            }
            if (entry.getPercentage().stripTrailingZeros().scale() > 2) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Milestone percentage can have at most two decimal places.");
            }
            if (entry.getBillingDate() == null) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Billing date is required for each milestone payment entry.");
            }
            if (!billingDates.add(entry.getBillingDate())) {
                throw new GlobalExceptionHandler.ValidationException(
                        "More than one milestone payment entry has the billing date " + entry.getBillingDate()
                                + ". Each billing date can have only one billing occurrence - give each milestone its own date.");
            }
            totalPercentage = totalPercentage.add(entry.getPercentage());
        }

        if (totalPercentage.compareTo(HUNDRED) > 0) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Total milestone percentage cannot exceed 100%. Current total: " + totalPercentage + "%");
        }
    }

    /**
     * Locks the plan configuration's active occurrences and returns the plan's active entries whose billing has started,
     * keyed by PMS milestone id. Call it FIRST in update and delete (before anything is changed), and only for MILESTONES
     * plans - the other structures never take these locks.
     *
     * Why not a query that joins entries to occurrences: under MySQL's default REPEATABLE READ every plain read uses the
     * snapshot taken at the transaction's first read, so a transition the scheduler committed after that point is
     * invisible. The locking read below returns the latest committed rows, and the decision is made in Java from exactly
     * those rows - no later plain read is consulted. The occurrence is matched to its entry by the same link occurrence
     * generation uses: same configuration (the locked set) and period start = period end = the entry's billing date. The
     * locks are held until commit or rollback, so the scheduler cannot move a locked occurrence to TAX_PENDING while the
     * edit is in flight; it waits, then sees the committed result (a configuration now PENDING_APPROVAL is not
     * progressed, see BillingOccurrenceTransactionServiceImpl).
     *
     * Lock order: occurrence rows (ascending id) first, then the milestone rows (validateMilestoneReferences), as the
     * scheduler takes only one occurrence row and no milestone row lock.
     */
    private Map<Long, BillingPaymentEntry> lockOccurrencesAndFindFrozenEntries(BillingMilestonePlan plan) {
        List<BillingSchedule> locked = billingScheduleRepository.findActiveByConfigurationForUpdate(plan.getBillingConfiguration());
        Map<java.time.LocalDate, BillingSchedule> occurrences = singleDayOccurrences(locked);

        Map<Long, BillingPaymentEntry> frozen = new LinkedHashMap<>();
        if (occurrences.values().stream().noneMatch(BillingMilestonePlanServiceImpl::billingStarted)) {
            return frozen;
        }
        for (BillingPaymentEntry entry : billingPaymentEntryRepository.findByMilestonePlanAndIsActiveTrue(plan)) {
            if (entry.getPmsMilestoneId() != null && isFrozen(entry, occurrences)) {
                frozen.put(entry.getPmsMilestoneId(), entry);
            }
        }
        return frozen;
    }

    // ---- THE frozen-entry rule: used by update/delete (on locked rows) and by the plan response (on plain reads) ----

    /** Billing has started for an occurrence: it is flagged invoiced or is in TAX_PENDING / TAX_CALCULATED / INVOICED. */
    public static boolean billingStarted(BillingSchedule schedule) {
        return Boolean.TRUE.equals(schedule.getIsInvoiced()) || IN_BILLING_STATUSES.contains(schedule.getPeriodStatus());
    }

    /**
     * The active single-day occurrences by date: the link between an entry and its occurrence is the configuration (the
     * caller passes only that configuration's rows) plus period start = period end = the entry's billing date. At most one
     * can exist per date - uk_billing_schedule_period is unique on (configuration, start, end) - so there is no ambiguity;
     * should a duplicate ever appear, the one whose billing has started wins, the safe choice.
     */
    public static Map<java.time.LocalDate, BillingSchedule> singleDayOccurrences(Collection<BillingSchedule> schedules) {
        Map<java.time.LocalDate, BillingSchedule> byDate = new java.util.HashMap<>();
        for (BillingSchedule schedule : schedules) {
            if (Boolean.TRUE.equals(schedule.getIsActive())
                    && schedule.getPeriodStartDate() != null
                    && Objects.equals(schedule.getPeriodStartDate(), schedule.getPeriodEndDate())) {
                byDate.merge(schedule.getPeriodStartDate(), schedule,
                        (a, b) -> billingStarted(a) || !billingStarted(b) ? a : b);
            }
        }
        return byDate;
    }

    /** An entry is frozen when the occurrence on its billing date has started billing; no occurrence means not frozen. */
    public static boolean isFrozen(BillingPaymentEntry entry, Map<java.time.LocalDate, BillingSchedule> occurrences) {
        BillingSchedule occurrence = occurrences.get(entry.getBillingDate());
        return occurrence != null && billingStarted(occurrence);
    }

    /**
     * The entries of {@code plan} whose billing has already started (see {@link #IN_BILLING_STATUSES}), keyed by PMS
     * milestone id, after checking that the edit leaves them alone: the request must repeat each of them with the same
     * percentage, billing date and sequence, must stay a MILESTONES plan, and must keep the total contract value they were
     * priced against (their amounts are frozen, so a different total would make their percentages meaningless). Empty for
     * a plan that is not (or was never) a MILESTONES plan, so FULL_PAYMENT and INSTALLMENTS edits behave as before.
     */
    private Map<Long, BillingPaymentEntry> freezeEntriesInBilling(
            BillingMilestonePlan plan, BillingMilestonePlanRequestDto request) {

        if (plan.getPaymentStructure() != PaymentStructure.MILESTONES) {
            return new LinkedHashMap<>();
        }

        Map<Long, BillingPaymentEntry> frozen = lockOccurrencesAndFindFrozenEntries(plan);
        if (frozen.isEmpty()) {
            return frozen;
        }

        if (request.getPaymentStructure() != PaymentStructure.MILESTONES) {
            throw new GlobalExceptionHandler.ValidationException(
                    "The payment structure cannot be changed: billing has already started for PMS milestone "
                            + frozen.keySet().iterator().next() + ".");
        }
        if (plan.getTotalContractValue() != null
                && request.getTotalContractValue().compareTo(plan.getTotalContractValue()) != 0) {
            throw new GlobalExceptionHandler.ValidationException(
                    "The total contract value cannot be changed (" + plan.getTotalContractValue() + ") because billing has "
                            + "already started for PMS milestone " + frozen.keySet().iterator().next() + ".");
        }

        Map<Long, BillingPaymentEntryRequestDto> requested = request.getEntries().stream()
                .collect(Collectors.toMap(BillingPaymentEntryRequestDto::getPmsMilestoneId, e -> e, (a, b) -> a));
        for (BillingPaymentEntry entry : frozen.values()) {
            BillingPaymentEntryRequestDto sent = requested.get(entry.getPmsMilestoneId());
            if (sent == null) {
                throw new GlobalExceptionHandler.ValidationException(
                        "PMS milestone " + entry.getPmsMilestoneId() + " is already in billing or invoiced and cannot be "
                                + "removed from the plan. Send it unchanged with the other entries.");
            }
            if (sent.getPercentage().compareTo(entry.getPercentage()) != 0
                    || !Objects.equals(sent.getBillingDate(), entry.getBillingDate())
                    || !Objects.equals(sent.getSequence(), entry.getSequence())) {
                throw new GlobalExceptionHandler.ValidationException(
                        "PMS milestone " + entry.getPmsMilestoneId() + " is already in billing or invoiced; its percentage ("
                                + entry.getPercentage() + "%), billing date (" + entry.getBillingDate() + ") and sequence ("
                                + entry.getSequence() + ") cannot be changed.");
            }
        }
        return frozen;
    }

    /**
     * Checks the PMS milestones a plan refers to against the synchronised data, on the server whatever the client sent:
     * each must exist, must not be deleted in PMS, must belong to the plan's project, and must not already be claimed by
     * another live billing plan. Plans of any other payment structure must not carry a milestone id at all.
     *
     * Concurrency: the referenced milestone rows are locked (FOR UPDATE, ascending id order) before the conflict query
     * runs, and the conflict query is a locking read, so two transactions claiming the same milestone are serialised and
     * the second sees the first's committed plan. This relies on the caller's transaction (create and update are
     * transactional) - the locks are released at commit or rollback. The database has no partial unique index to back this
     * up (MySQL does not support them).
     *
     * Eligibility: a milestone that is newly selected must be billable NOW - {@code billable = true} and
     * {@code billingReadiness = READY_FOR_BILLING} (see {@link #ineligibilityReason}). A milestone that is not ready yet is
     * simply not selectable; it becomes selectable when PMS marks it ready, by a later edit of the plan. The milestone's
     * {@code executionStatus} is deliberately NOT checked: AR has no documented rule for any of its values, and PMS already
     * folds completion into billingReadiness.
     *
     * {@code frozenMilestoneIds} are the milestones whose billing has already started in this plan (always empty on create).
     * They are exempt from the eligibility and deletion rules - PMS can no longer change what has been billed - but still
     * have to belong to the plan's project.
     *
     * Duplicate billing: a milestone that has reached billing in any configuration, whatever that configuration's status,
     * cannot be selected again; neither can one reserved by another live plan.
     */
    private void validateMilestoneReferences(
            BillingConfiguration configuration, BillingMilestonePlanRequestDto request, Set<Long> frozenMilestoneIds) {

        List<BillingPaymentEntryRequestDto> entries = request.getEntries();

        if (request.getPaymentStructure() != PaymentStructure.MILESTONES) {
            if (entries != null && entries.stream().anyMatch(e -> e.getPmsMilestoneId() != null)) {
                throw new GlobalExceptionHandler.ValidationException(
                        "pmsMilestoneId can only be set when the payment structure is MILESTONES.");
            }
            return;
        }

        ProjectMasterReference project = configuration.getProject();
        if (project == null || project.getPmsProjectId() == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "A MILESTONES plan needs a billing configuration that is linked to a project.");
        }

        // validateMilestoneEntries has already guaranteed: not empty, ids present and unique
        List<Long> milestoneIds = entries.stream()
                .map(BillingPaymentEntryRequestDto::getPmsMilestoneId)
                .sorted()
                .toList();

        Map<Long, ProjectMilestoneReference> found = new LinkedHashMap<>();
        for (ProjectMilestoneReference milestone : projectMilestoneReferenceRepository.findAllByIdForUpdate(milestoneIds)) {
            found.put(milestone.getPmsMilestoneId(), milestone);
        }

        for (Long milestoneId : milestoneIds) {
            ProjectMilestoneReference milestone = found.get(milestoneId);
            if (milestone == null) {
                throw new GlobalExceptionHandler.ValidationException(
                        "PMS milestone " + milestoneId + " was not found. Only milestones synchronised from PMS can be billed.");
            }
            boolean frozen = frozenMilestoneIds.contains(milestoneId);
            if (!frozen && Boolean.TRUE.equals(milestone.getSourceDeleted())) {
                throw new GlobalExceptionHandler.ValidationException(
                        "PMS milestone " + describe(milestone) + " has been deleted in PMS and cannot be used in a billing plan.");
            }
            if (!Objects.equals(milestone.getPmsProjectId(), project.getPmsProjectId())) {
                throw new GlobalExceptionHandler.ValidationException(
                        "PMS milestone " + describe(milestone) + " belongs to a different project and cannot be used in this project billing plan.");
            }
            String reason = frozen ? null : ineligibilityReason(milestone);
            if (reason != null) {
                throw new GlobalExceptionHandler.ValidationException(
                        "PMS milestone " + describe(milestone) + " " + reason + " and cannot be billed yet.");
            }
        }

        List<Long> candidateIds = milestoneIds.stream().filter(id -> !frozenMilestoneIds.contains(id)).toList();
        if (!candidateIds.isEmpty()) {
            List<BillingPaymentEntry> billed = billingPaymentEntryRepository.findBilledAssignments(candidateIds, IN_BILLING_STATUSES);
            if (!billed.isEmpty()) {
                throw new GlobalExceptionHandler.ValidationException(
                        "PMS milestone " + describe(found.get(billed.get(0).getPmsMilestoneId()))
                                + " has already been billed (billing configuration "
                                + billed.get(0).getMilestonePlan().getBillingConfiguration().getBillingConfigurationId()
                                + ") and cannot be billed again.");
            }
        }

        List<BillingPaymentEntry> conflicts = billingPaymentEntryRepository
                .findActiveAssignmentsInOtherConfigurations(milestoneIds, configuration.getBillingConfigurationId());
        if (!conflicts.isEmpty()) {
            BillingPaymentEntry conflict = conflicts.get(0);
            throw new GlobalExceptionHandler.ValidationException(
                    "PMS milestone " + describe(found.get(conflict.getPmsMilestoneId()))
                            + " is already part of another active billing plan (billing configuration "
                            + conflict.getMilestonePlan().getBillingConfiguration().getBillingConfigurationId() + ").");
        }
    }

    @Override
    @Transactional
    public void revalidateMilestoneClaims(BillingConfiguration configuration) {
        BillingMilestonePlan plan = billingMilestonePlanRepository.findByBillingConfigurationAndIsActiveTrue(configuration)
                .orElse(null);
        if (plan == null || plan.getPaymentStructure() != PaymentStructure.MILESTONES) {
            return;
        }

        List<Long> milestoneIds = billingPaymentEntryRepository.findByMilestonePlanAndIsActiveTrue(plan).stream()
                .map(BillingPaymentEntry::getPmsMilestoneId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (milestoneIds.isEmpty()) {
            return;
        }

        // Same lock order as update/delete: the configuration's occurrence rows first (they decide which entries are frozen,
        // from the latest committed rows - the same rule as plan update and delete), then the milestone rows (ascending),
        // then the locking claim reads. The scheduler takes one occurrence row and no milestone lock, so no cycle.
        Set<Long> frozenIds = lockOccurrencesAndFindFrozenEntries(plan).keySet();
        List<ProjectMilestoneReference> locked = projectMilestoneReferenceRepository.findAllByIdForUpdate(milestoneIds);

        // Entries already in billing are untouched by what PMS does now; every other entry has to still be selectable, so
        // a milestone PMS has since deleted or taken out of READY_FOR_BILLING blocks submission/approval of the plan until
        // it is removed (or PMS makes it ready again). The check stays silent about milestones the lock read did not return.
        for (ProjectMilestoneReference milestone : locked) {
            if (frozenIds.contains(milestone.getPmsMilestoneId())) {
                continue;
            }
            String reason = Boolean.TRUE.equals(milestone.getSourceDeleted())
                    ? "has been deleted in PMS"
                    : ineligibilityReason(milestone);
            if (reason != null) {
                throw new GlobalExceptionHandler.ValidationException(
                        "PMS milestone " + describe(milestone) + " " + reason + " and can no longer be billed. Remove it from "
                                + "the Milestone Plan (or wait until PMS marks it ready for billing again) before this "
                                + "configuration is submitted or approved.");
            }
        }

        // A milestone another configuration has already billed or has billing under way for - whatever that configuration's
        // status, an expired one included - can never be claimed again. The plan's own frozen entries are exempt (their
        // billing is this plan's); every other entry of this plan has no started occurrence by the locked rows above.
        List<Long> candidateIds = milestoneIds.stream().filter(id -> !frozenIds.contains(id)).toList();
        if (!candidateIds.isEmpty()) {
            List<BillingPaymentEntry> billed = billingPaymentEntryRepository.findBilledAssignments(candidateIds, IN_BILLING_STATUSES);
            if (!billed.isEmpty()) {
                BillingPaymentEntry b = billed.get(0);
                throw new GlobalExceptionHandler.ValidationException(
                        "PMS milestone " + b.getPmsMilestoneId() + " has already been billed (billing configuration "
                                + b.getMilestonePlan().getBillingConfiguration().getBillingConfigurationId()
                                + ") and cannot be billed again. Remove it from the Milestone Plan before this configuration "
                                + "is submitted or approved.");
            }
        }

        List<BillingPaymentEntry> conflicts = billingPaymentEntryRepository
                .findActiveAssignmentsInOtherConfigurations(milestoneIds, configuration.getBillingConfigurationId());
        if (!conflicts.isEmpty()) {
            BillingPaymentEntry conflict = conflicts.get(0);
            throw new GlobalExceptionHandler.ValidationException(
                    "PMS milestone " + conflict.getPmsMilestoneId()
                            + " is already part of another active billing plan (billing configuration "
                            + conflict.getMilestonePlan().getBillingConfiguration().getBillingConfigurationId() + ").");
        }
    }

    /**
     * Why a (not deleted) milestone cannot be newly billed, phrased to follow "PMS milestone X ...", or null when it can.
     * Package-visible: the milestone listing reports the same verdict, so the screen and the server never disagree.
     */
    static String ineligibilityReason(ProjectMilestoneReference milestone) {
        if (!Boolean.TRUE.equals(milestone.getBillable())) {
            return "is not billable in PMS";
        }
        if (!READY_FOR_BILLING.equals(milestone.getBillingReadiness())) {
            return "is not ready for billing in PMS (billing readiness: "
                    + (milestone.getBillingReadiness() == null ? "unknown" : milestone.getBillingReadiness()) + ")";
        }
        return null;
    }

    private static String describe(ProjectMilestoneReference milestone) {
        String code = milestone.getMilestoneCode();
        return milestone.getPmsMilestoneId() + (code == null || code.isBlank() ? "" : " (" + code + ")");
    }

    private List<BillingPaymentEntry> createEntries(
            BillingMilestonePlan milestonePlan,
            PaymentStructure paymentStructure,
            BigDecimal totalContractValue,
            List<BillingPaymentEntryRequestDto> entryRequests,
            Map<Long, BillingPaymentEntry> frozenEntries) {

        if (entryRequests == null || entryRequests.isEmpty()) {
            throw new GlobalExceptionHandler.ValidationException(
                    "At least one payment entry is required.");
        }

        if (paymentStructure == PaymentStructure.MILESTONES) {
            return createMilestoneEntries(milestonePlan, totalContractValue, entryRequests, frozenEntries);
        }

        // Sort by sequence to ensure proper order
        List<BillingPaymentEntryRequestDto> sortedEntries = entryRequests.stream()
                .sorted(Comparator.comparing(BillingPaymentEntryRequestDto::getSequence))
                .collect(Collectors.toList());

        List<BillingPaymentEntry> entries = new ArrayList<>();
        BigDecimal allocatedAmount = BigDecimal.ZERO;

        for (int i = 0; i < sortedEntries.size(); i++) {
            BillingPaymentEntryRequestDto request = sortedEntries.get(i);
            boolean isLastEntry = (i == sortedEntries.size() - 1);

            BigDecimal amount;
            if (isLastEntry) {
                // Last entry gets the remainder to ensure total matches exactly
                amount = totalContractValue.subtract(allocatedAmount).setScale(2, RoundingMode.HALF_EVEN);
            } else {
                // Calculate amount from percentage
                amount = totalContractValue
                        .multiply(request.getPercentage())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_EVEN);
                allocatedAmount = allocatedAmount.add(amount);
            }

            BillingPaymentEntry entry = BillingPaymentEntry.builder()
                    .milestonePlan(milestonePlan)
                    .sequence(request.getSequence())
                    .percentage(request.getPercentage())
                    .amount(amount)
                    .billingDate(request.getBillingDate())
                    .remarks(request.getRemarks())
                    .pmsMilestoneId(paymentStructure == PaymentStructure.MILESTONES ? request.getPmsMilestoneId() : null)
                    .isActive(true)
                    .build();

            entries.add(entry);
        }

        return entries;
    }

    /**
     * Entries of a MILESTONES plan, for the requested milestones that are not frozen (a frozen entry already exists and is
     * not recreated). Percentages are shares of the plan's total contract value (the same convention as FULL_PAYMENT and
     * INSTALLMENTS), so each amount is {@code total * percentage / 100} rounded HALF_EVEN to two decimals - the last entry
     * does NOT absorb "the rest" as it does for the other structures, because the plan may deliberately bill only part of
     * the contract.
     *
     * Remaining balance: frozen amounts plus the new amounts must not exceed the total, and every new amount must be
     * positive. Only when the whole plan is allocated (percentages add up to exactly 100) is the rounding drift of the
     * individual amounts settled on the last new entry, so a fully allocated plan bills exactly the contract value - the
     * same convention as the other structures. A frozen entry is never adjusted.
     */
    private List<BillingPaymentEntry> createMilestoneEntries(
            BillingMilestonePlan milestonePlan,
            BigDecimal totalContractValue,
            List<BillingPaymentEntryRequestDto> entryRequests,
            Map<Long, BillingPaymentEntry> frozenEntries) {

        BigDecimal committed = frozenEntries.values().stream()
                .map(BillingPaymentEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPercentage = BigDecimal.ZERO;

        List<BillingPaymentEntry> created = new ArrayList<>();
        BigDecimal newlyAllocated = BigDecimal.ZERO;

        for (BillingPaymentEntryRequestDto request : entryRequests.stream()
                .sorted(Comparator.comparing(BillingPaymentEntryRequestDto::getSequence)).toList()) {
            totalPercentage = totalPercentage.add(request.getPercentage());
            if (frozenEntries.containsKey(request.getPmsMilestoneId())) {
                continue;
            }

            BigDecimal amount = totalContractValue
                    .multiply(request.getPercentage())
                    .divide(HUNDRED, 2, RoundingMode.HALF_EVEN);
            newlyAllocated = newlyAllocated.add(amount);

            created.add(BillingPaymentEntry.builder()
                    .milestonePlan(milestonePlan)
                    .sequence(request.getSequence())
                    .percentage(request.getPercentage())
                    .amount(amount)
                    .billingDate(request.getBillingDate())
                    .remarks(request.getRemarks())
                    .pmsMilestoneId(request.getPmsMilestoneId())
                    .isActive(true)
                    .build());
        }

        if (totalPercentage.compareTo(HUNDRED) == 0 && !created.isEmpty()) {
            BillingPaymentEntry last = created.get(created.size() - 1);
            BigDecimal settled = totalContractValue.subtract(committed).subtract(newlyAllocated.subtract(last.getAmount()))
                    .setScale(2, RoundingMode.HALF_EVEN);
            newlyAllocated = newlyAllocated.subtract(last.getAmount()).add(settled);
            last.setAmount(settled);
        }

        for (BillingPaymentEntry entry : created) {
            if (entry.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new GlobalExceptionHandler.ValidationException(
                        "The amount of PMS milestone " + entry.getPmsMilestoneId() + " (" + entry.getPercentage()
                                + "% of " + totalContractValue + ") rounds to zero or less. Use a larger percentage.");
            }
        }

        BigDecimal remaining = totalContractValue.subtract(committed);
        if (newlyAllocated.compareTo(remaining) > 0) {
            throw new GlobalExceptionHandler.ValidationException(
                    "The selected milestones total " + newlyAllocated + ", which exceeds the remaining contract value of "
                            + remaining + " (total " + totalContractValue + ", already allocated to billed milestones "
                            + committed + ").");
        }

        return created;
    }

    private void handleApprovalStateTransition(BillingConfiguration configuration) {
        ApprovalStatus currentStatus = configuration.getApprovalStatus();

        switch (currentStatus) {
            case DRAFT:
                // Stay in DRAFT - no change needed
                break;

            case PENDING_APPROVAL:
                // Stay in PENDING_APPROVAL - already waiting for approval
                break;

            case APPROVED:
                // APPROVED + ACTIVE → PENDING_APPROVAL + INACTIVE
                configuration.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
                configuration.setBillingStatus(BillingConfigurationStatus.INACTIVE);
                configuration.setManuallyDeactivated(false);
                configuration.setRejectionReason(null);
                break;

            case REJECTED:
                // REJECTED → DRAFT (allow correction and resubmission)
                configuration.setApprovalStatus(ApprovalStatus.DRAFT);
                configuration.setBillingStatus(BillingConfigurationStatus.INACTIVE);
                configuration.setRejectionReason(null);
                break;
        }
    }

    private BillingMilestonePlanResponseDto mapToResponse(
            BillingMilestonePlan milestonePlan,
            List<BillingPaymentEntry> entries) {
        return mapToResponse(milestonePlan, entries, true);
    }

    private BillingMilestonePlanResponseDto mapToResponse(
            BillingMilestonePlan milestonePlan,
            List<BillingPaymentEntry> entries,
            boolean withOccurrences) {

        // Only MILESTONES entries carry an occurrence-derived state. A plain (non-locking) read is enough for display; the
        // decision itself is always re-made under lock by update/delete. Nothing here consults PMS, so a milestone that
        // PMS has since deleted does not hide the state of its entry.
        Map<java.time.LocalDate, BillingSchedule> occurrences = null;
        if (withOccurrences && milestonePlan.getPaymentStructure() == PaymentStructure.MILESTONES) {
            occurrences = singleDayOccurrences(billingScheduleRepository
                    .findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(milestonePlan.getBillingConfiguration()));
        }

        final Map<java.time.LocalDate, BillingSchedule> occurrencesByDate = occurrences;
        List<BillingPaymentEntryResponseDto> entryDtos = entries.stream()
                .map(entry -> mapEntryToResponse(entry, occurrencesByDate))
                .collect(Collectors.toList());

        return BillingMilestonePlanResponseDto.builder()
                .milestonePlanId(milestonePlan.getMilestonePlanId())
                .billingConfigurationId(
                        milestonePlan.getBillingConfiguration().getBillingConfigurationId()
                )
                .totalContractValue(milestonePlan.getTotalContractValue())
                .paymentStructure(milestonePlan.getPaymentStructure())
                .entries(entryDtos)
                .remarks(milestonePlan.getRemarks())
                .isActive(milestonePlan.getIsActive())
                .createdAt(milestonePlan.getCreatedAt())
                .updatedAt(milestonePlan.getUpdatedAt())
                .build();
    }

    /** {@code occurrences} is null for entries whose frozen state is not evaluated (FULL_PAYMENT / INSTALLMENTS, new plans). */
    private BillingPaymentEntryResponseDto mapEntryToResponse(
            BillingPaymentEntry entry, Map<java.time.LocalDate, BillingSchedule> occurrences) {
        BillingSchedule occurrence = occurrences == null ? null : occurrences.get(entry.getBillingDate());
        return BillingPaymentEntryResponseDto.builder()
                .frozen(occurrences == null ? null : isFrozen(entry, occurrences))
                .occurrenceStatus(occurrence == null ? null : occurrence.getPeriodStatus())
                .invoiced(occurrences == null ? null : occurrence != null && Boolean.TRUE.equals(occurrence.getIsInvoiced()))
                .paymentEntryId(entry.getPaymentEntryId())
                .milestonePlanId(entry.getMilestonePlan().getMilestonePlanId())
                .sequence(entry.getSequence())
                .percentage(entry.getPercentage())
                .amount(entry.getAmount())
                .billingDate(entry.getBillingDate())
                .remarks(entry.getRemarks())
                .pmsMilestoneId(entry.getPmsMilestoneId())
                .isActive(entry.getIsActive())
                .createdAt(entry.getCreatedAt())
                .updatedAt(entry.getUpdatedAt())
                .build();
    }

}
