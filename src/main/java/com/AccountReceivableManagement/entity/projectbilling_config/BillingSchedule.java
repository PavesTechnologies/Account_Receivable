package com.AccountReceivableManagement.entity.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingScheduleType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "billing_schedule",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_billing_schedule_period",
                        columnNames = {
                                "billing_configuration_id",
                                "period_start_date",
                                "period_end_date"
                        }
                ),
                @UniqueConstraint(
                        name = "uk_billing_schedule_recurring_period",
                        columnNames = {
                                "subscription_configuration_id",
                                "period_start_date",
                                "period_end_date"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "billing_schedule_id")
    private UUID billingScheduleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "billing_configuration_id",
            referencedColumnName = "billing_configuration_id",
            nullable = false
    )
    private BillingConfiguration billingConfiguration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "subscription_configuration_id",
            referencedColumnName = "subscription_configuration_id"
    )
    private BillingRecurringConfiguration recurringConfiguration;

    @Column(name = "period_number", nullable = false)
    private Integer periodNumber;

    @Column(name = "period_start_date", nullable = false)
    private LocalDate periodStartDate;

    @Column(name = "period_end_date", nullable = false)
    private LocalDate periodEndDate;

    @Column(name = "billing_date", nullable = false)
    private LocalDate billingDate;

    @Column(name = "billing_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal billingAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false)
    private BillingScheduleType scheduleType;

    @Column(name = "is_partial_period", nullable = false)
    @Builder.Default
    private Boolean isPartialPeriod = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_status", nullable = false)
    private BillingPeriodStatus periodStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_status", nullable = false)
    @Builder.Default
    private BillingPeriodStatus taxStatus = BillingPeriodStatus.PENDING;

    @Column(name = "is_invoiced", nullable = false)
    @Builder.Default
    private Boolean isInvoiced = false;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if (isActive == null) {
            isActive = true;
        }

        if (isPartialPeriod == null) {
            isPartialPeriod = false;
        }

        if (isInvoiced == null) {
            isInvoiced = false;
        }
    }

    /**
     * Whether this occurrence is due and still unbilled on {@code asOf}:
     * active, not invoiced, billing date on or before {@code asOf} (so
     * overdue occurrences missed while nothing ran are included), and in a
     * lifecycle state that still leads to an invoice - SCHEDULED (due, not yet
     * moved to TAX_PENDING), TAX_PENDING or TAX_CALCULATED. The billing date
     * is when the occurrence becomes billable, not when it must be processed.
     */
    public boolean isDueForBilling(LocalDate asOf) {
        return Boolean.TRUE.equals(isActive)
                && !Boolean.TRUE.equals(isInvoiced)
                && billingDate != null
                && asOf != null
                && !billingDate.isAfter(asOf)
                && UNBILLED_STATUSES.contains(periodStatus);
    }

    /**
     * Period statuses of an occurrence that still leads to an invoice - the
     * status part of {@link #isDueForBilling(LocalDate)}, shared with the
     * repository query that finds due occurrences.
     */
    public static final Set<BillingPeriodStatus> UNBILLED_STATUSES = EnumSet.of(
            BillingPeriodStatus.SCHEDULED,
            BillingPeriodStatus.TAX_PENDING,
            BillingPeriodStatus.TAX_CALCULATED);

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
