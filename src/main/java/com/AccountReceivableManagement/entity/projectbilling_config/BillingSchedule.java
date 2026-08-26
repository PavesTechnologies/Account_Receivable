package com.AccountReceivableManagement.entity.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingScheduleType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_schedule")
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

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
