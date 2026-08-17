package com.AccountReceivableManagement.entity.billing_data_acquisition;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingAcquisitionStatus;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.TriggerMode;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a Billing Acquisition execution record.
 * Tracks operational status (WAITING_FOR_SOURCE_DATA, READY, ALREADY_BILLED)
 * and invoice tracking per billing period for active configurations.
 */
@Entity
@Table(
        name = "billing_acquisition",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_billing_acq_config_period",
                columnNames = {"billing_configuration_id", "billing_period_start", "billing_period_end"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingAcquisition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "billing_configuration_id",
            referencedColumnName = "billing_configuration_id",
            nullable = false
    )
    private BillingConfiguration billingConfiguration;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "billing_period_start", nullable = false)
    private LocalDate billingPeriodStart;

    @Column(name = "billing_period_end", nullable = false)
    private LocalDate billingPeriodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_mode", nullable = false)
    private TriggerMode triggerMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BillingAcquisitionStatus status;

    @Column(name = "final_invoice_id")
    private String finalInvoiceId;

    @Column(name = "snapshot_id")
    private UUID snapshotId;

    @Column(name = "acquired_at")
    private LocalDateTime acquiredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.acquiredAt == null && this.status == BillingAcquisitionStatus.READY) {
            this.acquiredAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
