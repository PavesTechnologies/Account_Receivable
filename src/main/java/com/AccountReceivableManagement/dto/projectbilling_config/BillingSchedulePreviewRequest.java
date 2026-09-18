package com.AccountReceivableManagement.dto.projectbilling_config;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only request for the Billing Schedule Preview endpoint.
 * Either billingConfigurationId (to fall back on an existing configuration's
 * saved values) or the explicit fields below must be supplied.
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillingSchedulePreviewRequest {

    private UUID billingConfigurationId;

    /**
     * "FIXED_PRICE" or "RECURRING" (case-insensitive).
     */
    private String billingType;

    private UUID billingFrequencyId;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private BigDecimal contractValue;
}
