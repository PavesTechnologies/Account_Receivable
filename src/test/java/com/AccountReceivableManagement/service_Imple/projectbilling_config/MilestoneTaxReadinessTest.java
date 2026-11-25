package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.entity.project_entity.ProjectMilestoneReference;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.MilestoneTaxReadiness;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gate that decides whether a PMS milestone may be handed to tax calculation:
 * COMPLETED in PMS and its planned due date today or past. Both fields come straight from CDC
 * (pms.project_milestones -> project_milestone_reference), so the rule is pure and tested on its own.
 */
class MilestoneTaxReadinessTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);

    private ProjectMilestoneReference milestone(String executionStatus, LocalDate plannedDueDate) {
        return ProjectMilestoneReference.builder()
                .pmsMilestoneId(9101L)
                .pmsProjectId(9002L)
                .milestoneCode("M1")
                .executionStatus(executionStatus)
                .plannedDueDate(plannedDueDate)
                .build();
    }

    private MilestoneTaxReadiness readiness(String executionStatus, LocalDate plannedDueDate) {
        return BillingOccurrenceTransactionServiceImpl.taxReadiness(milestone(executionStatus, plannedDueDate), TODAY);
    }

    private String holdReason(String executionStatus, LocalDate plannedDueDate) {
        return BillingOccurrenceTransactionServiceImpl
                .taxReadinessHoldReason(milestone(executionStatus, plannedDueDate), TODAY);
    }

    @Test
    void completedAndDueToday_isReadyForTax() {
        assertThat(readiness("COMPLETED", TODAY)).isEqualTo(MilestoneTaxReadiness.READY_FOR_TAX);
        assertThat(holdReason("COMPLETED", TODAY)).isNull();
    }

    @Test
    void completedAndDueInThePast_isReadyForTax() {
        assertThat(readiness("COMPLETED", TODAY.minusDays(30))).isEqualTo(MilestoneTaxReadiness.READY_FOR_TAX);
        assertThat(holdReason("COMPLETED", TODAY.minusDays(30))).isNull();
    }

    @Test
    void completedButDueInTheFuture_isUpcoming() {
        assertThat(readiness("COMPLETED", TODAY.plusDays(1))).isEqualTo(MilestoneTaxReadiness.UPCOMING);
        assertThat(holdReason("COMPLETED", TODAY.plusDays(1)))
                .isEqualTo("is completed but upcoming: its planned due date is 2026-11-25");
    }

    /** An unfinished milestone is never taxed, however long its planned due date has been past. */
    @Test
    void notCompleted_isHeldEvenWhenOverdue() {
        assertThat(readiness("IN_PROGRESS", TODAY.minusDays(60))).isEqualTo(MilestoneTaxReadiness.NOT_COMPLETED);
        assertThat(holdReason("IN_PROGRESS", TODAY.minusDays(60)))
                .isEqualTo("is not completed in PMS (execution status: IN_PROGRESS)");
    }

    @Test
    void unknownExecutionStatus_isHeld() {
        assertThat(readiness(null, TODAY.minusDays(1))).isEqualTo(MilestoneTaxReadiness.NOT_COMPLETED);
        assertThat(holdReason(null, TODAY.minusDays(1)))
                .isEqualTo("is not completed in PMS (execution status: unknown)");
    }

    /** execution_status is kept as free text from PMS, so the comparison must not depend on case or padding. */
    @Test
    void completedIsMatchedCaseInsensitively() {
        assertThat(readiness("completed", TODAY)).isEqualTo(MilestoneTaxReadiness.READY_FOR_TAX);
        assertThat(readiness("  Completed  ", TODAY)).isEqualTo(MilestoneTaxReadiness.READY_FOR_TAX);
    }

    /**
     * PMS milestone 22 as synchronised: COMPLETED and READY_FOR_BILLING, finished on 2026-11-24 but planned for
     * 2026-12-11. It reads UPCOMING every day up to and including 2026-12-10, then becomes READY_FOR_TAX on the
     * planned due date itself - by the calendar moving, with no event, no job and no edit to the row. The occurrence
     * scheduler re-reads this on every run, so the milestone is handed to tax calculation on 2026-12-11 by itself.
     */
    @Test
    void completedEarlyMilestoneBecomesReadyOnItsPlannedDueDate() {
        LocalDate plannedDueDate = LocalDate.of(2026, 12, 11);
        ProjectMilestoneReference milestone22 = ProjectMilestoneReference.builder()
                .pmsMilestoneId(22L)
                .pmsProjectId(37L)
                .milestoneCode("AP-297-M003")
                .milestoneName("Dev")
                .executionStatus("COMPLETED")
                .billingReadiness("READY_FOR_BILLING")
                .billable(true)
                .actualCompletionDate(LocalDate.of(2026, 11, 24))
                .plannedDueDate(plannedDueDate)
                .sourceDeleted(false)
                .build();

        for (LocalDate day : List.of(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 12, 10))) {
            assertThat(BillingOccurrenceTransactionServiceImpl.taxReadiness(milestone22, day))
                    .as("on " + day + " it is still ahead of its planned due date")
                    .isEqualTo(MilestoneTaxReadiness.UPCOMING);
            assertThat(BillingOccurrenceTransactionServiceImpl.taxReadinessHoldReason(milestone22, day)).isNotNull();
        }

        for (LocalDate day : List.of(plannedDueDate, plannedDueDate.plusDays(1))) {
            assertThat(BillingOccurrenceTransactionServiceImpl.taxReadiness(milestone22, day))
                    .as("from " + plannedDueDate + " on it is ready")
                    .isEqualTo(MilestoneTaxReadiness.READY_FOR_TAX);
            assertThat(BillingOccurrenceTransactionServiceImpl.taxReadinessHoldReason(milestone22, day)).isNull();
        }
    }

    /** Nothing places it in the future, and holding it would stall its billing with no date that could release it. */
    @Test
    void completedWithNoPlannedDueDate_isReadyForTax() {
        assertThat(readiness("COMPLETED", null)).isEqualTo(MilestoneTaxReadiness.READY_FOR_TAX);
        assertThat(holdReason("COMPLETED", null)).isNull();
    }
}
