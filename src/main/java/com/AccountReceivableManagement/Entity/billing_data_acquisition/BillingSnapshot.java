package com.AccountReceivableManagement.Entity.billing_data_acquisition;

import com.AccountReceivableManagement.Entity_Enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.Entity_Enums.billing_data_acquisition.BillingType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Epic 2's owned resource. Holds a point-in-time copy of the Billing
 * Configuration commercial facts needed to bill for one project and one
 * billing period, plus the acquired {@link BillingSnapshotItem} lines.
 * Only ever persisted after successful acquisition and validation.
 */
@Entity
@Table(
        name = "billing_snapshot",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_billing_snapshot_project_period",
                columnNames = {"project_id", "billing_period_start", "billing_period_end"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingSnapshot {

    @Id
    @GeneratedValue
    @Column(name = "billing_snapshot_id")
    private UUID id;

    @Column(name = "snapshot_number", nullable = false, unique = true, length = 30)
    private String snapshotNumber;

    @Column(name = "billing_configuration_id", nullable = false)
    private UUID billingConfigurationId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_type", nullable = false, length = 40)
    private BillingType billingType;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "payment_term_code", length = 20)
    private String paymentTermCode;

    @Column(name = "billing_frequency", length = 30)
    private String billingFrequency;

    @Column(name = "tax_region_code", length = 20)
    private String taxRegionCode;

    @Column(name = "billing_period_start", nullable = false)
    private LocalDate billingPeriodStart;

    @Column(name = "billing_period_end", nullable = false)
    private LocalDate billingPeriodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BillingSnapshotStatus status;

    @Column(name = "subtotal", precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "expense_amount", precision = 19, scale = 2)
    private BigDecimal expenseAmount;

    @Column(name = "total_amount", precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Builder.Default
    @OneToMany(mappedBy = "billingSnapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BillingSnapshotItem> items = new ArrayList<>();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_date")
    private LocalDateTime createdDate;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_date")
    private LocalDateTime updatedDate;
}
