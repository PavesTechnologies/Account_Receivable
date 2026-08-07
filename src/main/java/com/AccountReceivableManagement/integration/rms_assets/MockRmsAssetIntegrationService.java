package com.AccountReceivableManagement.integration.rms_assets;

import com.AccountReceivableManagement.dto.rms_assets.ProjectBillableAssetResponseDto;
import com.AccountReceivableManagement.entity_enums.tool_catalog.BillingBasis;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;

/**
 * Stand-in for RMS's project asset assignment API until RMS integration is
 * available. Returns a small fixed set of static assignments across a couple
 * of projects, so downstream billing-eligibility flows can be exercised
 * end-to-end before RMS exists.
 * <p>
 * Replace with RMS REST client later. When RMS is available, only this class
 * needs to change - replace its body with an RMS REST API client that still
 * implements {@link RmsAssetIntegrationService} and returns the same
 * {@link ProjectBillableAssetResponseDto} shape. No controller, DTO, or
 * business logic elsewhere depends on this class directly; everything
 * depends on the {@link RmsAssetIntegrationService} interface.
 */
@Component
public class MockRmsAssetIntegrationService implements RmsAssetIntegrationService {

    private static final UUID POWER_BI_ASSET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID JIRA_ASSET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GITHUB_ASSET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID VPN_LICENSE_ASSET_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID ANTIVIRUS_ASSET_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private static final Map<Long, List<ProjectBillableAssetResponseDto>> MOCK_PROJECT_ASSETS = Map.of(
            101L, List.of(
                    ProjectBillableAssetResponseDto.builder()
                            .assetId(POWER_BI_ASSET_ID)
                            .assetCode("PBI-PRO")
                            .assetName("Power BI Pro")
                            .assetCategory("Software")
                            .quantity(10)
                            .billingBasis(BillingBasis.RECURRING)
                            .assignmentStartDate(LocalDate.of(2026, 1, 1))
                            .assignmentEndDate(null)
                            .billableEligible(true)
                            .build(),
                    ProjectBillableAssetResponseDto.builder()
                            .assetId(JIRA_ASSET_ID)
                            .assetCode("JIRA-PREM")
                            .assetName("Jira Premium")
                            .assetCategory("Software")
                            .quantity(5)
                            .billingBasis(BillingBasis.RECURRING)
                            .assignmentStartDate(LocalDate.of(2026, 1, 1))
                            .assignmentEndDate(null)
                            .billableEligible(true)
                            .build(),
                    ProjectBillableAssetResponseDto.builder()
                            .assetId(VPN_LICENSE_ASSET_ID)
                            .assetCode("VPN-LIC")
                            .assetName("VPN License")
                            .assetCategory("License")
                            .quantity(50)
                            .billingBasis(BillingBasis.RECURRING)
                            .assignmentStartDate(LocalDate.of(2026, 1, 1))
                            .assignmentEndDate(null)
                            .billableEligible(false)
                            .build()
            ),
            102L, List.of(
                    ProjectBillableAssetResponseDto.builder()
                            .assetId(GITHUB_ASSET_ID)
                            .assetCode("GH-ENT")
                            .assetName("GitHub Enterprise")
                            .assetCategory("Software")
                            .quantity(20)
                            .billingBasis(BillingBasis.RECURRING)
                            .assignmentStartDate(LocalDate.of(2026, 2, 1))
                            .assignmentEndDate(null)
                            .billableEligible(true)
                            .build(),
                    ProjectBillableAssetResponseDto.builder()
                            .assetId(ANTIVIRUS_ASSET_ID)
                            .assetCode("AV-STD")
                            .assetName("Antivirus Standard")
                            .assetCategory("License")
                            .quantity(100)
                            .billingBasis(BillingBasis.ONE_TIME)
                            .assignmentStartDate(LocalDate.of(2026, 2, 1))
                            .assignmentEndDate(LocalDate.of(2026, 12, 31))
                            .billableEligible(false)
                            .build()
            )
    );

    @Override
    public List<ProjectBillableAssetResponseDto> getBillableAssetsForProject(Long projectId) {

        return MOCK_PROJECT_ASSETS.getOrDefault(projectId, Collections.emptyList());
    }
}
