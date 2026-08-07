package com.AccountReceivableManagement.dto.asset_lookup;

import lombok.*;

import java.util.UUID;

/**
 * Shape of an asset record as returned by whatever backs {@code AssetLookupService}
 * (today {@code MockAssetLookupService}, later the real RMS Asset Master API).
 * Tool Pricing Configuration only reads {@code assetCode}/{@code assetName} from
 * this today; {@code assetCategory} is carried for future consumers.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetLookupResponseDto {

    private UUID assetId;

    private String assetCode;

    private String assetName;

    private String assetCategory;
}
