package com.AccountReceivableManagement.strategy.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionResultDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotCreateRequestDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.TimesheetDto;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTMRateCard;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingRatePeriod;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.PricingModel;
import com.AccountReceivableManagement.integration.billing_data_acquisition.TimesheetIntegration;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTMRateCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Acquires approved timesheets from TMS for Time & Material billing
 * and retrieves active hourly rates from billing_tm_rate_card for the configuration.
 * <p>
 * Timesheets are billed in hours, so only rate cards priced per hour are
 * used: a DAILY/WEEKLY/MONTHLY card's rate would otherwise be multiplied by
 * hours. No conversion is made (the model has no hours-per-day rule) - a
 * role with only non-hourly cards gets no rate and the acquisition is
 * rejected by {@code BillingAcquisitionValidator}.
 */
@Component
@RequiredArgsConstructor
public class TimeAndMaterialBillingStrategy implements BillingAcquisitionStrategy {

    private final TimesheetIntegration timesheetIntegration;
    private final BillingTMRateCardRepository billingTMRateCardRepository;

    @Override
    public BillingType getSupportedBillingType() {
        return BillingType.TIME_AND_MATERIAL;
    }

    @Override
    public BillingAcquisitionResultDto acquire(BillingConfigurationResponseDto configuration, BillingSnapshotCreateRequestDto request) {
        List<TimesheetDto> timesheets = timesheetIntegration.getApprovedTimesheets(
                request.getProjectId(), request.getBillingPeriodStart(), request.getBillingPeriodEnd());

        if (timesheets == null) {
            timesheets = Collections.emptyList();
        }

        UUID configId = request.getBillingConfigurationId() != null
                ? request.getBillingConfigurationId()
                : (configuration != null ? configuration.getBillingConfigurationId() : null);

        boolean roleBased = configuration != null
                && configuration.getPricingModel() == PricingModel.ROLE_BASED;

        for (TimesheetDto timesheet : timesheets) {
            LocalDate workDate = timesheet.getWorkDate() != null ? timesheet.getWorkDate() : request.getBillingPeriodStart();

            BigDecimal hourlyRate = roleBased
                    ? resolveRoleBasedRate(configId, timesheet.getRole(), workDate)
                    : resolveStandardRate(configuration, configId, timesheet.getRole(), workDate);

            timesheet.setHourlyRate(hourlyRate);
        }

        return BillingAcquisitionResultDto.builder()
                .billingConfigurationId(configId)
                .timesheets(timesheets)
                .build();
    }

    /**
     * ROLE_BASED: the rate card row for the timesheet's role that is in
     * effect on its work date, so a rate card activated later never reprices
     * work already done. No fallback to another role, an out-of-date rate or
     * the configuration's hourly rate - an unmatched role leaves the rate
     * unset and {@code BillingAcquisitionValidator} rejects the acquisition.
     */
    private BigDecimal resolveRoleBasedRate(UUID configId, String role, LocalDate workDate) {
        if (configId == null || role == null || role.isBlank()) {
            return null;
        }
        List<BillingTMRateCard> effectiveRates =
                billingTMRateCardRepository.findActiveRatesByConfigurationAndDate(configId, workDate);
        if (effectiveRates == null) {
            return null;
        }
        String normalizedRole = role.trim();
        return effectiveRates.stream()
                .filter(TimeAndMaterialBillingStrategy::isHourly)
                .filter(rateCard -> rateCard.getRoleName() != null
                        && rateCard.getRoleName().trim().equalsIgnoreCase(normalizedRole))
                .map(BillingTMRateCard::getRate)
                .findFirst()
                .orElse(null);
    }

    /**
     * STANDARD (and configurations without a pricing model):
     * Deterministic resolution:
     * 1. Rate card effective on the work date matching the role (if role is specified).
     * 2. General rate card effective on the work date (roleName is null or blank).
     * 3. Fall back to configuration's contract hourly rate.
     * Never silently fall back to an unrelated role's rate card or an out-of-date active rate card.
     */
    private BigDecimal resolveStandardRate(BillingConfigurationResponseDto configuration, UUID configId,
            String role, LocalDate workDate) {
        BigDecimal hourlyRate = null;

        if (configId != null) {
            List<BillingTMRateCard> activeRates = hourlyOnly(billingTMRateCardRepository
                    .findActiveRatesByConfigurationAndDate(configId, workDate));

            if (!activeRates.isEmpty()) {
                if (role != null && !role.isBlank()) {
                    String normRole = role.trim();
                    for (BillingTMRateCard card : activeRates) {
                        if (card.getRoleName() != null && card.getRoleName().trim().equalsIgnoreCase(normRole)) {
                            hourlyRate = card.getRate();
                            break;
                        }
                    }
                }
                if (hourlyRate == null) {
                    for (BillingTMRateCard card : activeRates) {
                        if (card.getRoleName() == null || card.getRoleName().isBlank()) {
                            hourlyRate = card.getRate();
                            break;
                        }
                    }
                }
            }
        }

        if (hourlyRate == null && configuration != null && configuration.getHourlyRate() != null) {
            hourlyRate = configuration.getHourlyRate();
        }

        return hourlyRate;
    }

    /**
     * A card priced per hour. The column is mandatory; an unset period (only
     * possible for rows that bypassed it) is treated as hourly, as before.
     */
    private static boolean isHourly(BillingTMRateCard rateCard) {
        return rateCard.getRatePeriod() == null || rateCard.getRatePeriod() == BillingRatePeriod.HOURLY;
    }

    private static List<BillingTMRateCard> hourlyOnly(List<BillingTMRateCard> rateCards) {
        return rateCards == null ? List.of()
                : rateCards.stream().filter(TimeAndMaterialBillingStrategy::isHourly).toList();
    }
}
