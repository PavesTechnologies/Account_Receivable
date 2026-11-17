package com.AccountReceivableManagement.validator.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionResultDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.TimesheetDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.ValidationResultDto;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingAcquisitionValidatorTest {

    @Mock
    private BillingSnapshotItemRepository billingSnapshotItemRepository;

    private BillingAcquisitionValidator validator;

    private final UUID configurationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        validator = new BillingAcquisitionValidator(billingSnapshotItemRepository);
    }

    private TimesheetDto timesheet(String timesheetId) {
        return TimesheetDto.builder()
                .resourceId(7L)
                .resourceName("Asha Rao")
                .sourceReferenceId(timesheetId)
                .workDate(LocalDate.of(2026, 10, 6))
                .hours(new BigDecimal("8"))
                .hourlyRate(new BigDecimal("1500"))
                .role("Developer")
                .approvalStatus("APPROVED")
                .approved(true)
                .billable(true)
                .build();
    }

    private BillingAcquisitionResultDto result(TimesheetDto... timesheets) {
        return BillingAcquisitionResultDto.builder()
                .billingConfigurationId(configurationId)
                .timesheets(List.of(timesheets))
                .build();
    }

    @Test
    void validate_multipleApprovedBillableTimesheets_succeeds() {
        ValidationResultDto outcome = validator.validate(result(timesheet("TS-1"), timesheet("TS-2")));

        assertThat(outcome.isSuccess()).isTrue();
    }

    @Test
    void validate_nonBillableTimesheet_failsWholeAcquisition() {
        TimesheetDto nonBillable = timesheet("TS-2");
        nonBillable.setBillable(false);

        ValidationResultDto outcome = validator.validate(result(timesheet("TS-1"), nonBillable));

        assertThat(outcome.isSuccess()).isFalse();
        assertThat(outcome.getValidationMessage()).isEqualTo("Timesheet [TS-2] is not billable.");
        verify(billingSnapshotItemRepository, never()).findClaimedSourceReferenceIds(any(), any(), any(), any());
    }

    @Test
    void validate_unapprovedTimesheet_failsWholeAcquisition() {
        TimesheetDto submitted = timesheet("TS-3");
        submitted.setApproved(false);
        submitted.setApprovalStatus("SUBMITTED");

        ValidationResultDto outcome = validator.validate(result(submitted));

        assertThat(outcome.isSuccess()).isFalse();
        assertThat(outcome.getValidationMessage()).isEqualTo("Timesheet [TS-3] is not approved.");
    }

    @Test
    void validate_noTimesheets_failsAsBefore() {
        ValidationResultDto outcome = validator.validate(result());

        assertThat(outcome.isSuccess()).isFalse();
        assertThat(outcome.getValidationMessage())
                .isEqualTo("No timesheets were acquired for the requested billing period.");
    }

    @Test
    void validate_timesheetWithoutTmsId_isRejectedBecauseItCannotBeDeduplicated() {
        ValidationResultDto outcome = validator.validate(result(timesheet(null)));

        assertThat(outcome.isSuccess()).isFalse();
        assertThat(outcome.getValidationMessage()).contains("has no TMS timesheet id");
    }

    @Test
    void validate_missingRate_namesTheRole() {
        TimesheetDto unpriced = timesheet("TS-4");
        unpriced.setHourlyRate(null);
        unpriced.setRole("Senior Developer");

        ValidationResultDto outcome = validator.validate(result(unpriced));

        assertThat(outcome.isSuccess()).isFalse();
        assertThat(outcome.getValidationMessage()).isEqualTo(
                "No active hourly rate found for billing configuration " + configurationId
                        + " and work date 2026-10-06, role Senior Developer.");
    }

    @Test
    void validate_timesheetAlreadyOnAnotherSnapshot_isNotBilledAgain() {
        when(billingSnapshotItemRepository.findClaimedSourceReferenceIds(
                eq(BillingItemType.TIME_ENTRY), anyCollection(), eq(BillingSnapshotStatus.CANCELLED), isNull()))
                .thenReturn(List.of("TS-2"));

        ValidationResultDto outcome = validator.validate(result(timesheet("TS-1"), timesheet("TS-2")));

        assertThat(outcome.isSuccess()).isFalse();
        assertThat(outcome.getValidationMessage()).isEqualTo(
                "Timesheet(s) [TS-2] are already included in another Billing Snapshot and cannot be billed again.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void validate_checksEveryTimesheetIdInOneQuery() {
        ArgumentCaptor<Collection<String>> ids = ArgumentCaptor.forClass(Collection.class);
        when(billingSnapshotItemRepository.findClaimedSourceReferenceIds(
                eq(BillingItemType.TIME_ENTRY), ids.capture(), eq(BillingSnapshotStatus.CANCELLED), isNull()))
                .thenReturn(List.of());

        validator.validate(result(timesheet("TS-1"), timesheet("TS-2"), timesheet("TS-3")));

        assertThat(ids.getValue()).containsExactly("TS-1", "TS-2", "TS-3");
    }

    @Test
    void validate_rebuildingASnapshot_excludesThatSnapshotsOwnItems() {
        UUID owningSnapshotId = UUID.randomUUID();
        when(billingSnapshotItemRepository.findClaimedSourceReferenceIds(
                eq(BillingItemType.TIME_ENTRY), anyCollection(), eq(BillingSnapshotStatus.CANCELLED),
                eq(owningSnapshotId)))
                .thenReturn(List.of());

        ValidationResultDto outcome = validator.validate(result(timesheet("TS-1")), owningSnapshotId);

        assertThat(outcome.isSuccess()).isTrue();
    }
}
