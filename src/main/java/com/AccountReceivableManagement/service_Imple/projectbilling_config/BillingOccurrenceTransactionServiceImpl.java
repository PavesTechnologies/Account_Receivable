package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.entity.project_entity.ProjectMilestoneReference;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingPaymentEntry;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.repo.project.ProjectMilestoneReferenceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingPaymentEntryRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingOccurrenceTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of BillingOccurrenceTransactionService.
 * Each transition runs in its own transaction (REQUIRES_NEW) to ensure
 * that failures in one occurrence don't affect others.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingOccurrenceTransactionServiceImpl implements BillingOccurrenceTransactionService {

    private final BillingScheduleRepository billingScheduleRepository;
    private final BillingPaymentEntryRepository billingPaymentEntryRepository;
    private final ProjectMilestoneReferenceRepository projectMilestoneReferenceRepository;

    /**
     * SCHEDULED -> TAX_PENDING is where billing begins: from TAX_PENDING on, a milestone entry is frozen (see
     * BillingMilestonePlanServiceImpl.IN_BILLING_STATUSES), so this is the last point at which the occurrence can still be
     * held back. The passed schedule was read by the scheduler outside any transaction and may be stale, so the row is
     * re-read here under a row lock and everything is decided on that fresh copy. An occurrence that must not progress is
     * left untouched (still SCHEDULED, no exception, nothing to roll back) and is looked at again on the next run:
     * <ul>
     *   <li>the row is no longer SCHEDULED or active (another transaction moved, deactivated or deleted it);</li>
     *   <li>its configuration is not APPROVED - an edited configuration waits in PENDING_APPROVAL for Finance, and a
     *       configuration pending approval is never billable;</li>
     *   <li>it belongs to a Milestone Plan and its configuration has EXPIRED, its milestone is deleted / not billable /
     *       not READY_FOR_BILLING, or another configuration also holds or has billed the milestone
     *       (see {@link #milestoneHoldReason}). Other billing types are not affected by the expiry rule.</li>
     * </ul>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transitionSingleOccurrenceToTaxPending(BillingSchedule candidate) {
        BillingSchedule schedule = billingScheduleRepository.findByIdForUpdate(candidate.getBillingScheduleId()).orElse(null);
        if (schedule == null
                || schedule.getPeriodStatus() != BillingPeriodStatus.SCHEDULED
                || !Boolean.TRUE.equals(schedule.getIsActive())) {
            return;
        }

        BillingConfiguration configuration = schedule.getBillingConfiguration();
        if (configuration != null && configuration.getApprovalStatus() != ApprovalStatus.APPROVED) {
            log.info("Occurrence {} stays SCHEDULED: its billing configuration is {}, not APPROVED",
                    schedule.getBillingScheduleId(), configuration.getApprovalStatus());
            return;
        }

        String holdReason = milestoneHoldReason(schedule, configuration);
        if (holdReason != null) {
            log.warn("Occurrence {} stays SCHEDULED: {}", schedule.getBillingScheduleId(), holdReason);
            return;
        }

        schedule.setPeriodStatus(BillingPeriodStatus.TAX_PENDING);
        schedule.setTaxStatus(BillingPeriodStatus.TAX_PENDING);
        billingScheduleRepository.save(schedule);
        log.info("Transitioned occurrence {} to TAX_PENDING", schedule.getBillingScheduleId());
    }

    /** True for a configuration billed through a Milestone Plan. Package-visible: the approval-time promotion applies the same rule. */
    static boolean isMilestonePlan(BillingConfiguration configuration) {
        return configuration != null && configuration.getBillingType() != null
                && "Milestone Plan".equalsIgnoreCase(String.valueOf(configuration.getBillingType().getBillingTypeName()).trim());
    }

    /**
     * Why a Milestone Plan configuration cannot start billing an unstarted occurrence NOW, or null: its billing status is
     * INACTIVE (not started yet, or manually deactivated) or EXPIRED, or today is before its effective-from date. A missing
     * effective-from counts as not started, exactly as in the configuration status calculation
     * ({@code BillingConfigurationServiceImpl.calculateBillingStatus}, which returns INACTIVE for it). The date is checked
     * here as well as the stored status because the status is only recalculated by a scheduler once a minute (and at
     * approval), so it can lag the calendar. Shared by the scheduled transition and the approval-time promotion so both
     * enforce the same rule; null for every other billing type, whose behaviour is unchanged. Dates are not compared with the
     * occurrence's billing date: an occurrence dated before effective-from is held until the configuration starts and then
     * progresses like any overdue one (catch-up), no billing date is rejected.
     */
    static String milestoneNotYetBillableReason(BillingConfiguration configuration, java.time.LocalDate today) {
        if (!isMilestonePlan(configuration)) {
            return null;
        }
        if (configuration.getBillingStatus() == BillingConfigurationStatus.EXPIRED) {
            return "its billing configuration has EXPIRED";
        }
        if (configuration.getBillingStatus() == BillingConfigurationStatus.INACTIVE) {
            return "its billing configuration is INACTIVE";
        }
        if (configuration.getEffectiveFrom() == null) {
            return "its billing configuration has no effective-from date";
        }
        if (today.isBefore(configuration.getEffectiveFrom())) {
            return "its billing configuration starts on " + configuration.getEffectiveFrom();
        }
        return null;
    }

    /**
     * Why the milestone this occurrence bills (if any) cannot be billed now, or null. Only Milestone Plan occurrences are
     * looked at; the rules, in order:
     * <ol>
     *   <li>The configuration is EXPIRED, INACTIVE or not yet effective (see {@link #milestoneNotYetBillableReason}).
     *       An EXPIRED configuration bills nothing more. The claim rules already treat EXPIRED as finished (its milestones
     *       are released to other configurations), so an occurrence that has not started billing must not progress either -
     *       otherwise the released milestone could be invoiced twice. Occurrences already past SCHEDULED are not touched.</li>
     *   <li>The milestone is deleted, not billable or not READY_FOR_BILLING in PMS.</li>
     *   <li>Another configuration now holds a live claim on the milestone, or has already started billing it. Both
     *       configurations would otherwise be able to invoice it (e.g. a configuration that expired, released the milestone
     *       and became ACTIVE again when PMS extended the project end date). Both sides then wait; nothing is cancelled.</li>
     * </ol>
     */
    private String milestoneHoldReason(BillingSchedule schedule, BillingConfiguration configuration) {
        if (!isMilestonePlan(configuration)) {
            return null;
        }
        String notBillable = milestoneNotYetBillableReason(configuration, java.time.LocalDate.now());
        if (notBillable != null) {
            return notBillable;
        }
        // an occurrence of a milestone plan is a single day: the entry's billing date
        if (!schedule.getPeriodStartDate().equals(schedule.getPeriodEndDate())) {
            return null;
        }

        List<BillingPaymentEntry> entries = billingPaymentEntryRepository
                .findActiveMilestoneEntries(configuration, schedule.getPeriodStartDate());
        for (BillingPaymentEntry entry : entries) {
            ProjectMilestoneReference milestone = projectMilestoneReferenceRepository.findById(entry.getPmsMilestoneId()).orElse(null);
            if (milestone == null) {
                return "PMS milestone " + entry.getPmsMilestoneId() + " is not synchronised";
            }
            if (Boolean.TRUE.equals(milestone.getSourceDeleted())) {
                return "PMS milestone " + entry.getPmsMilestoneId() + " has been deleted in PMS";
            }
            String reason = BillingMilestonePlanServiceImpl.ineligibilityReason(milestone);
            if (reason != null) {
                return "PMS milestone " + entry.getPmsMilestoneId() + " " + reason;
            }
        }

        List<Long> milestoneIds = entries.stream().map(BillingPaymentEntry::getPmsMilestoneId).sorted().toList();
        if (!milestoneIds.isEmpty()) {
            if (!billingPaymentEntryRepository.findBilledAssignments(milestoneIds, BillingMilestonePlanServiceImpl.IN_BILLING_STATUSES).isEmpty()) {
                return "PMS milestone " + milestoneIds.get(0) + " has already been billed by another billing configuration";
            }
            if (!billingPaymentEntryRepository
                    .findActiveAssignmentsInOtherConfigurations(milestoneIds, configuration.getBillingConfigurationId()).isEmpty()) {
                return "PMS milestone " + milestoneIds.get(0) + " is also held by another live billing configuration";
            }
        }
        return null;
    }
}
