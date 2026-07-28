package com.AccountReceivableManagement.entity.projectbilling_config;

import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;

import jakarta.persistence.*;
import lombok.*;

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
    @Column(name = "status")
    private BillingConfigurationStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "billing_type_id",
            referencedColumnName = "billing_type_id",
            nullable = false
    )
    private BillingTypeMaster billingType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "currency_id",
            referencedColumnName = "currency_id",
            nullable = false
    )
    private CurrencyMaster currency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "payment_term_id",
            referencedColumnName = "payment_term_id",
            nullable = false
    )
    private PaymentTermsMaster paymentTerm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "billing_frequency_id",
            referencedColumnName = "billing_frequency_id",
            nullable = false
    )
    private BillingFrequencyMaster billingFrequency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "tax_region_id",
            referencedColumnName = "tax_region_id",
            nullable = false
    )
    private TaxRegionMaster taxRegion;

    @Column(name = "expense_billing_eligible", nullable = false)
    @Builder.Default
    private Boolean expenseBillingEligible = false;

    @Column(name = "is_active")
    private Boolean isActive;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
