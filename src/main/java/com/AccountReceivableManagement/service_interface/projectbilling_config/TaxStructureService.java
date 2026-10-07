package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.TaxStructureResponseDto;

import java.util.UUID;

/**
 * Service for tax structure discovery API.
 * Allows frontend to dynamically discover available tax regimes and components
 * for a given tax region without hardcoding country-specific logic.
 */
public interface TaxStructureService {

    /**
     * Get the complete tax structure for a tax region.
     * Returns available tax regimes and their components.
     *
     * @param taxRegionId the tax region ID
     * @return the tax structure including regimes and components
     */
    TaxStructureResponseDto getTaxStructureByRegion(UUID taxRegionId);
}
