package com.AccountReceivableManagement.dto.billing_data_acquisition;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for the Billing Data Acquisition overview table.
 * Read-only view of ACTIVE billing configurations, with acquisition-level
 * status and lastInvoice resolved from the matching Billing Acquisition Record.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingDataAcquisitionResponseDto {

    /** Billing configuration primary key */
    private UUID billingConfigurationId;

    /** PMS project identifier */
    private Long projectId;

    /** Project display name */
    private String projectName;

    /** Project code – formatted as PRJ-{projectId} when no dedicated code exists */
    private String projectCode;

    /** Client display name */
    private String clientName;

    /** Billing type name from billing_type_master (e.g. "Timesheet Based", "Fixed Price") */
    private String billingType;

    /** Billing frequency name from billing_frequency_master (e.g. "Monthly", "Weekly") */
    private String frequency;

    /** Currency code (e.g. "INR", "USD") */
    private String currency;

    /**
     * Start date of the billing period the user selected on Acquire Snapshot
     * for the latest acquisition of this configuration. Null when the
     * configuration has never been acquired. Never derived from the project
     * or Billing Configuration's own validity/effective dates.
     * Serialized as ISO date string "YYYY-MM-DD".
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate billingPeriodStart;

    /**
     * End date of the billing period the user selected on Acquire Snapshot
     * for the latest acquisition of this configuration. Null when the
     * configuration has never been acquired. Never derived from the project
     * or Billing Configuration's own validity/effective dates.
     * Serialized as ISO date string "YYYY-MM-DD".
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate billingPeriodEnd;

    /**
     * Invoice generation mode from invoice_generation_type column.
     * Values: MANUAL | AUTOMATIC
     */
    private String generationMode;

    /**
     * Acquisition-level status ({@link com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingAcquisitionStatus}
     * name), resolved from the matching Billing Acquisition Record.
     * Defaults to "NOT_ACQUIRED" when no matching record exists yet.
     */
    private String status;

    /**
     * Final invoice identifier from the matching Billing Acquisition Record, if any.
     * Included explicitly as null in JSON (not omitted) so the frontend
     * can rely on its presence.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String lastInvoice;
}
