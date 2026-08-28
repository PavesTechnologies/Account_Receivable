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

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "billing_configuration")
@Entity
public class BillingConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "billing_configuration_id")
    private UUID billingConfigurationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "client_id",
            referencedColumnName = "client_id",
            nullable = false
    )
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "project_id",
            referencedColumnName = "pms_project_id",
            nullable = false
    )
    private ProjectMasterReference project;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false)
    @Builder.Default
    private ApprovalStatus approvalStatus = ApprovalStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_status", nullable = false)
    @Builder.Default
    private BillingConfigurationStatus billingStatus =
            BillingConfigurationStatus.INACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "billing_type_id", referencedColumnName = "billing_type_id",
            nullable = false
    )
    private BillingTypeMaster billingType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "currency_id",
            referencedColumnName = "currency_id",
            nullable = true
    )
    private CurrencyMaster currency;

//    @Column(name = "currency")
//    private String currency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "payment_term_id",
            referencedColumnName = "payment_term_id",
            nullable = true
    )
    private PaymentTermsMaster paymentTerm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "billing_frequency_id",
            referencedColumnName = "billing_frequency_id",
            nullable = true
    )
    private BillingFrequencyMaster billingFrequency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "tax_region_id",
            referencedColumnName = "tax_region_id",
            nullable = true
    )
    private TaxRegionMaster taxRegion;

    @Column(name = "contract_value", precision = 19, scale = 2)
    private BigDecimal contractValue;

    @Column(name = "expense_billing_eligible", nullable = false)
    @Builder.Default
    private Boolean expenseBillingEligible = false;


    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @Column(name = "hourly_rate")
    private BigDecimal hourlyRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_generation_type", nullable = true)
    private InvoiceGenerationType invoiceGenerationType;


    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_model")
    private PricingModel pricingModel;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "manually_deactivated", nullable = false)
    @Builder.Default
    private Boolean manuallyDeactivated = false;
}
