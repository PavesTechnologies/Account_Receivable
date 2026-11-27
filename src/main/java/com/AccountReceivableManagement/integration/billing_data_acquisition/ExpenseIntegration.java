package com.AccountReceivableManagement.integration.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.ExpenseLineDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Isolates billing acquisition from how client-billable expenses are
 * obtained (currently XMS). Returns only validated lines; invalid or
 * inconsistent data fails the acquisition with an actionable error.
 */
public interface ExpenseIntegration {

    /**
     * @param expectedCurrency currency of the billing configuration; every
     *                         expense must already be in it (no conversion in AR)
     */
    List<ExpenseLineDto> getBillableExpenses(Long projectId, LocalDate periodStart,
                                             LocalDate periodEnd, String expectedCurrency);

    /**
     * As above, with explicit treatment of expenses already on a live Billing Snapshot: with
     * {@code scope.skipAlreadyClaimed()} an exact repeat of an already-billed handoff is left out instead of
     * failing the acquisition (a corrected handoff of a billed expense still fails). Expenses on
     * {@code scope.owningSnapshotId()} do not count as billed elsewhere.
     */
    default List<ExpenseLineDto> getBillableExpenses(Long projectId, LocalDate periodStart, LocalDate periodEnd,
            String expectedCurrency,
            com.AccountReceivableManagement.dto.billing_data_acquisition.AcquisitionScope scope) {
        if (scope != null && (scope.skipAlreadyClaimed() || scope.owningSnapshotId() != null)) {
            throw new UnsupportedOperationException("This expense integration does not support incremental acquisition.");
        }
        return getBillableExpenses(projectId, periodStart, periodEnd, expectedCurrency);
    }
}
