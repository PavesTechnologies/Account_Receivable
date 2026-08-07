package com.AccountReceivableManagement.integration.asset_lookup;

import com.AccountReceivableManagement.dto.asset_lookup.AssetLookupResponseDto;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Stand-in for RMS's Asset Master API until RMS integration is available.
 * Returns a small fixed set of static assets keyed by a fixed UUID each, so
 * the Tool Pricing flow can be exercised end-to-end before RMS exists.
 * <p>
 * When RMS is available, only this class needs to change - replace its body
 * with an RMS REST API client that still implements {@link AssetLookupService}
 * and returns the same {@link AssetLookupResponseDto} shape. No controller,
 * entity, DTO, repository, or business logic elsewhere depends on this class
 * directly; everything depends on the {@link AssetLookupService} interface.
 */
@Component
public class MockAssetLookupService implements AssetLookupService {

    private static final UUID POWER_BI_ASSET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID JIRA_ASSET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GITHUB_ASSET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final Map<UUID, AssetLookupResponseDto> MOCK_ASSETS = Map.of(
            POWER_BI_ASSET_ID, AssetLookupResponseDto.builder()
                    .assetId(POWER_BI_ASSET_ID)
                    .assetCode("PBI-PRO")
                    .assetName("Power BI Pro")
                    .assetCategory("Software")
                    .build(),
            JIRA_ASSET_ID, AssetLookupResponseDto.builder()
                    .assetId(JIRA_ASSET_ID)
                    .assetCode("JIRA-PREM")
                    .assetName("Jira Premium")
                    .assetCategory("Software")
                    .build(),
            GITHUB_ASSET_ID, AssetLookupResponseDto.builder()
                    .assetId(GITHUB_ASSET_ID)
                    .assetCode("GH-ENT")
                    .assetName("GitHub Enterprise")
                    .assetCategory("Software")
                    .build()
    );

    @Override
    public AssetLookupResponseDto getAssetById(UUID assetId) {

        AssetLookupResponseDto asset = MOCK_ASSETS.get(assetId);

        if (asset == null) {
            throw new GlobalExceptionHandler.ResourceNotFoundException("Asset not found.");
        }

        return asset;
    }
}
