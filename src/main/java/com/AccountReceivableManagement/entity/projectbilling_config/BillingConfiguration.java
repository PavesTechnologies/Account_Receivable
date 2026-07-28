package com.AccountReceivableManagement.entity.projectbilling_config;

import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
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
    @Column(name = "status")
    private BillingConfigurationStatus status;

    @Column(name = "is_active")
    private Boolean isActive;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_type")
    private BillingType billingType;

    @Column(name = "currency_code")
    private String currencyCode;

    @Column(name = "payment_term_code")
    private String paymentTermCode;

    @Column(name = "billing_frequency")
    private String billingFrequency;

    @Column(name = "tax_region_code")
    private String taxRegionCode;

    @Column(name = "hourly_rate")
    private BigDecimal hourlyRate;

    @Column(name = "contract_value")
    private BigDecimal contractValue;

    @Column(name = "expense_billing_eligible")
    private Boolean expenseBillingEligible;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
