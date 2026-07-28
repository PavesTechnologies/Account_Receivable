package com.AccountReceivableManagement.integration.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads the approved Billing Configuration from Epic 1. Epic 1 lives in
 * this same application (see {@code projectbilling_config}), so this is a
 * direct in-process call to its service — no HTTP hop, no separate base
 * URL, no token forwarding required. This is the only class in Epic 2 that
 * knows Epic 1's own response shape; everything downstream only ever sees
 * this class's own {@code BillingConfigurationResponseDto}.
 */
@Component
@RequiredArgsConstructor
public class BillingConfigurationIntegrationImpl implements BillingConfigurationIntegration {

    private final BillingConfigurationService billingConfigurationService;

    @Override
    public BillingConfigurationResponseDto getApprovedBillingConfiguration(Long projectId) {
        com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationResponseDto epic1Configuration =
                billingConfigurationService.getApprovedByProjectId(projectId);

        return BillingConfigurationResponseDto.builder()
                .billingConfigurationId(epic1Configuration.getBillingConfigurationId())
                .projectId(epic1Configuration.getProjectId())
                .billingType(epic1Configuration.getBillingType())
                .currencyCode(epic1Configuration.getCurrencyCode())
                .paymentTermCode(epic1Configuration.getPaymentTermCode())
                .billingFrequency(epic1Configuration.getBillingFrequency())
                .taxRegionCode(epic1Configuration.getTaxRegionCode())
                .hourlyRate(epic1Configuration.getHourlyRate())
                .expenseEligible(Boolean.TRUE.equals(epic1Configuration.getExpenseBillingEligible()))
                .approved(epic1Configuration.getStatus() == BillingConfigurationStatus.APPROVED)
                .build();
    }
}
