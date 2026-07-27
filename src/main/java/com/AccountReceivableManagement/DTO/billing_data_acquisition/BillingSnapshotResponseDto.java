package com.AccountReceivableManagement.DTO.billing_data_acquisition;

import com.AccountReceivableManagement.Entity_Enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.Entity_Enums.billing_data_acquisition.BillingType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
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
     * Billing type read from Epic 1's approved Billing Configuration
     * at acquisition time.
     */
    private BillingType billingType;

    /**
     * ISO currency code (e.g. {@code USD}) copied from Epic 1 at acquisition time.
     */
    private String currencyCode;

    /**
     * Payment term code (e.g. {@code NET30}) copied from Epic 1 at acquisition time.
     */
    private String paymentTermCode;

    /**
     * Billing frequency copied from Epic 1 at acquisition time. Kept as a
     * plain string — Epic 1 owns this master data and hasn't finalized its
     * allowed value set, so Epic 2 does not model it as an enum.
     */
    private String billingFrequency;

    /**
     * Tax region code copied from Epic 1 at acquisition time.
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
}
