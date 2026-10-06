package com.AccountReceivableManagement.entity.projectbilling_config;

import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.*;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores a snapshot of the previous approved billing configuration
 * when an APPROVED+ACTIVE configuration is edited.
 * This preserves the last approved state for comparison and restoration.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "billing_configuration_snapshot")
@Entity
public class BillingConfigurationSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "snapshot_id")
    private UUID snapshotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "billing_configuration_id",
            referencedColumnName = "billing_configuration_id",
            nullable = false
    )
    private BillingConfiguration billingConfiguration;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_approval_status", nullable = false)
    private ApprovalStatus previousApprovalStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_billing_status", nullable = false)
    private BillingConfigurationStatus previousBillingStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_client_id", referencedColumnName = "client_id")
    private Client previousClient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_project_id", referencedColumnName = "pms_project_id")
    private ProjectMasterReference previousProject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_billing_type_id", referencedColumnName = "billing_type_id")
    private BillingTypeMaster previousBillingType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_currency_id", referencedColumnName = "currency_id")
    private CurrencyMaster previousCurrency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_payment_term_id", referencedColumnName = "payment_term_id")
    private PaymentTermsMaster previousPaymentTerm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_billing_frequency_id", referencedColumnName = "billing_frequency_id")
    private BillingFrequencyMaster previousBillingFrequency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_tax_region_id", referencedColumnName = "tax_region_id")
    private TaxRegionMaster previousTaxRegion;

    @Column(name = "previous_contract_value", precision = 19, scale = 2)
    private BigDecimal previousContractValue;

    @Column(name = "previous_expense_billing_eligible")
    private Boolean previousExpenseBillingEligible;

    @Column(name = "previous_effective_from")
    private LocalDate previousEffectiveFrom;

    @Column(name = "previous_effective_to")
    private LocalDate previousEffectiveTo;

    @Column(name = "previous_hourly_rate", precision = 19, scale = 2)
    private BigDecimal previousHourlyRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_invoice_generation_type")
    private InvoiceGenerationType previousInvoiceGenerationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_pricing_model")
    private PricingModel previousPricingModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_billing_context")
    private BillingContext previousBillingContext;

    @Column(name = "previous_product_name", length = 200)
    private String previousProductName;

    @Column(name = "previous_product_description", length = 500)
    private String previousProductDescription;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_restored", nullable = false)
    @Builder.Default
    private Boolean isRestored = false;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        if (isRestored == null) {
            isRestored = false;
        }
    }
}
