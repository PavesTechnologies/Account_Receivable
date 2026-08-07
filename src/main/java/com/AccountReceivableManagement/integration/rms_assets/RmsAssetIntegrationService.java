package com.AccountReceivableManagement.integration.rms_assets;

import com.AccountReceivableManagement.dto.rms_assets.ProjectBillableAssetResponseDto;

import java.util.List;

/**
 * Isolates AR from how RMS's project asset assignments are actually obtained.
 * RMS owns project asset assignments, quantities, assignment dates, and
 * billable eligibility - AR only consumes this data through this contract,
 * never through RMS's own API shape or knowledge of whether it currently
 * comes from a mock or a real HTTP call.
 */
public interface RmsAssetIntegrationService {

    /**
     * Retrieves the assets assigned to a project that RMS has marked as
     * billing-relevant.
     *
     * @param projectId AR project identifier
     * @return the project's billable assets, or an empty list if the project
     *         has none
     */
    List<ProjectBillableAssetResponseDto> getBillableAssetsForProject(Long projectId);
}
