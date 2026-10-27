package com.AccountReceivableManagement.dto.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillingPaymentEntryResponseDto {

    private UUID paymentEntryId;

    private UUID milestonePlanId;

    private Integer sequence;

    private BigDecimal percentage;

    private BigDecimal amount;

    private LocalDate billingDate;

    private String remarks;

    /** PMS milestone this entry bills; null for FULL_PAYMENT / INSTALLMENTS entries. */
    private Long pmsMilestoneId;

    private Boolean isActive;

    /**
     * MILESTONES plans only (null for FULL_PAYMENT / INSTALLMENTS entries): true when billing has started for this entry, so
     * it can be neither changed nor removed - exactly the rule plan update and delete enforce. False while there is no
     * occurrence yet or the occurrence is still SCHEDULED.
     */
    private Boolean frozen;

    /**
     * MILESTONES plans only: status of the active single-day occurrence generated for this entry (same configuration, period
     * start = period end = billing date), or null when none exists yet.
     */
    private BillingPeriodStatus occurrenceStatus;

    /** MILESTONES plans only: the matching occurrence is flagged invoiced (an invoice exists); false when there is none. */
    private Boolean invoiced;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
