package com.AccountReceivableManagement.dto.billing_data_acquisition;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One row of the paginated Billing Data Acquisition listing
 * ({@code GET /api/billing-data-acquisition}).
 *
 * Deliberately narrower than {@link BillingDataAcquisitionResponseDto}: only the
 * columns the listing table renders, so the page query does not have to resolve
 * currency, frequency or billing-period data it would not show.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingAcquisitionSummaryDto {

    /** Billing configuration primary key. */
    private UUID billingConfigurationId;

    /** Client display name. */
    private String clientName;

    /** Project display name. */
    private String projectName;

    /**
     * Project code as stored on {@code project_master_reference.project_code}.
     *
     * Unlike {@link BillingDataAcquisitionResponseDto#getProjectCode()}, which
     * synthesises a legacy {@code PRJ-{id}} string, this is the real column and is
     * null when the column is null. Serialized explicitly as null (not omitted) so
     * the frontend can rely on the key being present.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String projectCode;

    /** Billing type name from billing_type_master (e.g. "Timesheet Based"). */
    private String billingType;

    /**
     * Acquisition-level status
     * ({@link com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingAcquisitionStatus}
     * name), resolved from the matching Billing Acquisition Record.
     * "NOT_ACQUIRED" when no matching record exists yet.
     */
    private String status;
}
