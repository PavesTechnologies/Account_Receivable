package com.AccountReceivableManagement.integration.asset_lookup;

import com.AccountReceivableManagement.dto.asset_lookup.AssetLookupResponseDto;

import java.util.UUID;

/**
 * Isolates AR from how RMS Asset Master data is actually obtained. AR's
 * business logic depends only on this contract, never on RMS's own API shape
 * or on whether the data currently comes from a mock or a real HTTP call.
 */
public interface AssetLookupService {

    /**
     * Retrieves the asset identified by {@code assetId}.
     *
     * @param assetId RMS asset identifier
     * @return the asset's current code/name/category
     * @throws com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler.ResourceNotFoundException
     *         if no asset exists with that id
     */
    AssetLookupResponseDto getAssetById(UUID assetId);
}
