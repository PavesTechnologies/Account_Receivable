package com.AccountReceivableManagement.dto.billing_data_acquisition;

import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Response returned for a Billing Snapshot — by {@code POST /billing-snapshots}
 * on creation and by {@code GET /billing-snapshots/{snapshotId}} on retrieval.
 * Exposes only the snapshot summary defined by the current API contract;
 * line-item detail is a later story.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillingSnapshotResponseDto {

    /**
     * System-generated identifier of the Billing Snapshot.
     */
    private UUID snapshotId;

    /**
     * Acquisition status determined by the snapshot engine (e.g. READY | PARTIALLY_READY).
     */
    private String acquisitionStatus;

    /**
     * Human-readable, business-facing identifier (e.g. {@code BS-20260724153015}).
     */
    private String snapshotNumber;

    /**
     * Project this snapshot was acquired for.
     */
    private Long projectId;

    /**
     * Client this snapshot was acquired for.
     */
    private UUID clientId;

    /**
     * Billing Type master reference recorded on the snapshot at acquisition time.
     */
    private UUID billingTypeId;

    /**
     * Billing Type master name, resolved from Epic 1 at read time.
     */
    private String billingTypeName;

    /**
     * Currency master reference recorded on the snapshot at acquisition time.
     */
    private UUID currencyId;

    /**
     * ISO currency code (e.g. {@code USD}), resolved from Epic 1 at read time.
     */
    private String currencyCode;

    /**
     * Payment Terms master reference recorded on the snapshot at acquisition time.
     */
    private UUID paymentTermId;

    /**
     * Payment Terms master name, resolved from Epic 1 at read time.
     */
    private String paymentTermName;

    /**
     * Billing Frequency master reference recorded on the snapshot at acquisition time.
     */
    private UUID billingFrequencyId;

    /**
     * Billing Frequency master name, resolved from Epic 1 at read time.
     */
    private String billingFrequencyName;

    /**
     * Tax Region master reference recorded on the snapshot at acquisition time.
     */
    private UUID taxRegionId;

    /**
     * Tax region code, resolved from Epic 1 at read time.
     */
    private String taxRegionCode;

    /**
     * Start date of the billing period this snapshot covers.
     */
    private LocalDate billingPeriodStart;

    /**
     * End date of the billing period this snapshot covers.
     */
    private LocalDate billingPeriodEnd;

    /**
     * Sum of acquired billing items before expenses. Defaults to zero
     * rather than null when a billing type has no items to sum.
     */
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    /**
     * Sum of acquired expense items, if any. Defaults to zero rather than
     * null when there are no expenses (e.g. Story 2.1, Time & Material only).
     */
    @Builder.Default
    private BigDecimal expenseAmount = BigDecimal.ZERO;

    /**
     * Subtotal plus expense amount — the total this snapshot bills for.
     */
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /**
     * Current lifecycle status of the snapshot.
     */
    private BillingSnapshotStatus status;

    /**
     * Timesheet line items acquired from TMS — returned so the UI can
     * immediately render the Labor Charges Preview table without a
     * second round-trip. Each entry mirrors one TMS timesheet row with
     * the commercial rate merged in from the Billing Configuration.
     */
    @Builder.Default
    private List<TimesheetLineItemDto> timesheets = new ArrayList<>();


    /**
     * One row in the Labor Charges Preview table shown in AcquireDataStep.jsx.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TimesheetLineItemDto {
        /** Shown as "Employee" column. */
        private String employee;
        /** Shown as "Work Date" column (ISO YYYY-MM-DD). */
        private LocalDate workDate;
        /** Shown as "Hours" column. */
        private BigDecimal hours;
        /** Shown as "Rate" column — sourced from AR Billing Config, not TMS. */
        private BigDecimal rate;
        /** Shown as "Amount" column = hours × rate. */
        private BigDecimal amount;
        /** Shown as "Approval Status" column — always "APPROVED" per TMS contract. */
        private String approvalStatus;
        /** TMS source reference id for audit. */
        private String sourceReferenceId;
        /** Project role — used for ROLE_BASED billing rate lookup. */
        private String role;
    }
}
