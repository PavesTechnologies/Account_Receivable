package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingMilestonePlanRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingMilestonePlanResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;

import java.util.Optional;
import java.util.UUID;

public interface BillingMilestonePlanService {

    BillingMilestonePlanResponseDto create(UUID billingConfigurationId, BillingMilestonePlanRequestDto request);

    BillingMilestonePlanResponseDto update(UUID milestonePlanId, BillingMilestonePlanRequestDto request);

    BillingMilestonePlanResponseDto get(UUID milestonePlanId);

    BillingMilestonePlanResponseDto getByBillingConfiguration(UUID billingConfigurationId);

    Optional<BillingMilestonePlanResponseDto> findActiveByBillingConfiguration(BillingConfiguration configuration);

    void delete(UUID milestonePlanId);

    /**
     * Re-checks, under the milestone row locks, that no other live billing configuration has claimed any milestone this
     * configuration's active MILESTONES plan holds. Called at submission and at approval, because the claim made when the
     * plan was saved can have been overtaken since. Does nothing for configurations without an active MILESTONES plan.
     *
     * @throws com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler.ValidationException on a conflict
     */
    void revalidateMilestoneClaims(BillingConfiguration configuration);

}
