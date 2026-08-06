package com.AccountReceivableManagement.entity.projectbilling_config;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationUnit;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalPricingType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_subscription_configuration")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillingSubscriptionConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "subscription_configuration_id")
    private UUID subscriptionConfigurationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "billing_configuration_id",
            referencedColumnName = "billing_configuration_id",
            nullable = false
    )
    private BillingConfiguration billingConfiguration;

    @Column(name = "subscription_name", nullable = false, length = 200)
    private String subscriptionName;

    @Column(
            name = "contract_value",
            nullable = false,
            precision = 18,
            scale = 2
    )
    private BigDecimal contractValue;

    @Column(name = "subscription_start_date")
    private LocalDate subscriptionStartDate;

    @Column(name = "subscription_end_date")
    private LocalDate subscriptionEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "renewal_type")
    private RenewalType renewalType;

    @Enumerated(EnumType.STRING)
    @Column(name = "renewal_duration_type")
    private RenewalDurationType renewalDurationType;

    @Column(name = "renewal_duration_value")
    private Integer renewalDurationValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "renewal_duration_unit")
    private RenewalDurationUnit renewalDurationUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "renewal_pricing_type")
    private RenewalPricingType renewalPricingType;

    @Column(
            name = "renewal_contract_value",
            precision = 18,
            scale = 2
    )
    private BigDecimal renewalContractValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "renewal_billing_frequency_id",
            referencedColumnName = "billing_frequency_id"
    )
    private BillingFrequencyMaster renewalBillingFrequency;

    @Column(name = "renewal_effective_from")
    private LocalDate renewalEffectiveFrom;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
