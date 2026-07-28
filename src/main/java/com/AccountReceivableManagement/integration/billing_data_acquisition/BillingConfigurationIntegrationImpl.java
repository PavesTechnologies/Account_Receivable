package com.AccountReceivableManagement.integration.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Temporary stand-in for Epic 1's Billing Configuration API. Always returns
 * a hardcoded, approved Time &amp; Material configuration. To be replaced
 * with a real call to Epic 1 once that API is available — no other class
 * in Epic 2 should need to change when that happens.
 */
@Component
public class BillingConfigurationIntegrationImpl implements BillingConfigurationIntegration {

    private static final UUID MOCK_CONFIGURATION_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    // TODO: Replace with Epic 1 REST API integration once available.
    @Override
    public BillingConfigurationResponseDto getApprovedBillingConfiguration(Long projectId) {
        return BillingConfigurationResponseDto.builder()
                .billingConfigurationId(MOCK_CONFIGURATION_ID)
                .projectId(projectId)
                .billingType(BillingType.TIME_AND_MATERIAL)
                .currencyCode("USD")
                .paymentTermCode("NET30")
                .billingFrequency("MONTHLY")
                .taxRegionCode("US")
                .hourlyRate(BigDecimal.valueOf(100))
                .expenseEligible(true)
                .approved(true)
                .build();
    }
}
