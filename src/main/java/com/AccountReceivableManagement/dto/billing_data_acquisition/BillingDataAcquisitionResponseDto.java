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
 * Phase 1: read-only view of ACTIVE billing configurations.
 * Status is fixed as "READY" and lastInvoice is null until
 * Phase 2 introduces the Billing Acquisition Record.
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
     * Start of the billing period (effectiveFrom).
     * Serialized as ISO date string "YYYY-MM-DD".
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate billingPeriodStart;

    /**
     * End of the billing period (effectiveTo).
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
     * Phase 1 fixed value: always "READY".
     * Will be replaced by the acquisition lifecycle in Phase 2.
     */
    private String status;

    /**
     * Phase 1 fixed value: always null.
     * Will be populated from the Billing Acquisition Record in Phase 2.
     * Included explicitly as null in JSON (not omitted) so the frontend
     * can rely on its presence.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String lastInvoice;
}
