package com.AccountReceivableManagement.dto.rms_assets;

import com.AccountReceivableManagement.entity_enums.tool_catalog.BillingBasis;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Shape of a project's billable asset as returned by whatever backs
 * {@code RmsAssetIntegrationService} (today {@code MockRmsAssetIntegrationService},
 * later the real RMS Asset Master / Assignment API). Pure RMS operational data -
 * quantity, assignment window, billable eligibility - carries no AR pricing
 * (unit price, currency); Tool Pricing is resolved separately, keyed by assetId.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectBillableAssetResponseDto {

    private UUID assetId;

    private String assetCode;

    private String assetName;

    private String assetCategory;

    private Integer quantity;

    private BillingBasis billingBasis;

    private LocalDate assignmentStartDate;

    private LocalDate assignmentEndDate;

    private Boolean billableEligible;
}
