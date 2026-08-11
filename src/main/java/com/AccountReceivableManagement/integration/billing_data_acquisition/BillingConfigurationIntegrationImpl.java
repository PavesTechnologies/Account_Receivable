package com.AccountReceivableManagement.integration.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;
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

                .billingType(toBillingTypeEnum(
                        epic1Configuration.getBillingTypeName()))

                .billingTypeId(epic1Configuration.getBillingTypeId())
                .billingTypeName(epic1Configuration.getBillingTypeName())

                .currencyCode(epic1Configuration.getCurrency())

                .paymentTermId(epic1Configuration.getPaymentTermId())
                .paymentTermName(epic1Configuration.getPaymentTermName())

                .billingFrequencyId(epic1Configuration.getBillingFrequencyId())
                .billingFrequencyName(epic1Configuration.getBillingFrequencyName())

                .taxRegionId(epic1Configuration.getTaxRegionId())
                .taxRegionCode(epic1Configuration.getTaxRegionCode())

                .hourlyRate(epic1Configuration.getHourlyRate())

                .expenseEligible(
                        Boolean.TRUE.equals(
                                epic1Configuration.getExpenseBillingEligible()))

                .approved(
                        epic1Configuration.getStatus()
                                == BillingConfigurationStatus.APPROVED)

                .build();
    }

    /**
     * Maps a Billing Type master name onto the {@link BillingType} enum that
     * Epic 2 dispatches its acquisition strategies on, e.g.
     * {@code "Time & Material" -> TIME_AND_MATERIAL}. Returns {@code null}
     * when the master row has no corresponding enum constant.
     */
    private static BillingType toBillingTypeEnum(String billingTypeName) {
        if (billingTypeName == null) {
            return null;
        }

        String normalized = billingTypeName.trim()
                .replace("&", "AND")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .toUpperCase();

        try {
            return BillingType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
