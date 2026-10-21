package com.AccountReceivableManagement.validator.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionResultDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.TimesheetDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.ValidationResultDto;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Validates acquired Time &amp; Material data before it reaches the Builder.
 * Fail-fast: any rule violation immediately fails the whole acquisition —
 * invalid data is never silently dropped or cleaned up.
 */
@Component
@RequiredArgsConstructor
public class BillingAcquisitionValidator {

    private final BillingSnapshotItemRepository billingSnapshotItemRepository;

    /** First-time acquisition: no snapshot owns these timesheets yet. */
    public ValidationResultDto validate(BillingAcquisitionResultDto acquisitionResult) {
        return validate(acquisitionResult, null);
    }

    /**
     * @param owningSnapshotId the snapshot being rebuilt (financial
     *        correction), whose own items must not count as already billed;
     *        {@code null} for first-time acquisition
     */
    public ValidationResultDto validate(BillingAcquisitionResultDto acquisitionResult, UUID owningSnapshotId) {
        if (acquisitionResult == null) {
            return ValidationResultDto.failure("Billing acquisition result is missing.");
        }

        List<TimesheetDto> timesheets = acquisitionResult.getTimesheets();
        if (timesheets == null || timesheets.isEmpty()) {
            return ValidationResultDto.failure("No timesheets were acquired for the requested billing period.");
        }

        Set<String> seenSourceReferenceIds = new LinkedHashSet<>();
        for (TimesheetDto timesheet : timesheets) {
            if (timesheet.getSourceReferenceId() == null || timesheet.getSourceReferenceId().isBlank()) {
                return ValidationResultDto.failure(
                        "Timesheet of " + timesheet.getResourceName() + " on " + timesheet.getWorkDate()
                                + " has no TMS timesheet id, so it cannot be tracked against double billing.");
            }
            if (!timesheet.isApproved()) {
                return ValidationResultDto.failure(
                        "Timesheet [" + timesheet.getSourceReferenceId() + "] is not approved.");
            }
            if (!timesheet.isBillable()) {
                return ValidationResultDto.failure(
                        "Timesheet [" + timesheet.getSourceReferenceId() + "] is not billable.");
            }
            if (timesheet.getHours() == null || timesheet.getHours().compareTo(BigDecimal.ZERO) <= 0) {
                return ValidationResultDto.failure(
                        "Timesheet [" + timesheet.getSourceReferenceId() + "] has invalid hours.");
            }
            if (timesheet.getHourlyRate() == null || timesheet.getHourlyRate().compareTo(BigDecimal.ZERO) <= 0) {
                String configIdStr = acquisitionResult.getBillingConfigurationId() != null
                        ? acquisitionResult.getBillingConfigurationId().toString()
                        : "N/A";
                String roleSuffix = timesheet.getRole() != null && !timesheet.getRole().isBlank()
                        ? ", role " + timesheet.getRole().trim()
                        : "";
                return ValidationResultDto.failure(
                        "No active hourly rate found for billing configuration " + configIdStr + " and work date "
                                + timesheet.getWorkDate() + roleSuffix + ".");
            }
            if (!seenSourceReferenceIds.add(timesheet.getSourceReferenceId())) {
                return ValidationResultDto.failure(
                        "Timesheet [" + timesheet.getSourceReferenceId() + "] is a duplicate source reference.");
            }
        }

        // Source-level idempotency: one TMS timesheet is billed by at most one
        // Billing Snapshot, whatever period, trigger or retry produced it.
        List<String> alreadyBilled = billingSnapshotItemRepository.findClaimedSourceReferenceIds(
                BillingItemType.TIME_ENTRY,
                seenSourceReferenceIds,
                BillingSnapshotStatus.CANCELLED,
                owningSnapshotId);
        if (alreadyBilled != null && !alreadyBilled.isEmpty()) {
            return ValidationResultDto.failure(
                    "Timesheet(s) " + alreadyBilled + " are already included in another Billing Snapshot "
                            + "and cannot be billed again.");
        }

        return ValidationResultDto.success(acquisitionResult);
    }
}
