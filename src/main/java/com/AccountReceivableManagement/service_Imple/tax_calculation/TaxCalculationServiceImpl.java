package com.AccountReceivableManagement.service_Imple.tax_calculation;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.tax_calculation.TaxCalculationComponentResponseDto;
import com.AccountReceivableManagement.dto.tax_calculation.TaxCalculationResponseDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxConfigurationComponent;
import com.AccountReceivableManagement.entity.tax_calculation.TaxCalculation;
import com.AccountReceivableManagement.entity.tax_calculation.TaxCalculationComponent;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxApplicabilityType;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxCalculationStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRegionMasterRepository;
import com.AccountReceivableManagement.repo.tax_calculation.TaxCalculationRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingConfigurationService;
import com.AccountReceivableManagement.service_interface.tax_calculation.TaxCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Computes tax for an already-acquired {@link BillingSnapshot} and persists
 * the result as a {@link TaxCalculation}. Consumes the existing commercial
 * result rather than recalculating it: {@code BillingSnapshot.totalAmount}
 * (subtotal + expenseAmount) is copied as the taxable amount and is never
 * modified here. Tax applicability is configuration-driven off the
 * Phase 1 {@link TaxConfiguration} resolved for the snapshot's own
 * {@code taxRegionId} and {@code billingPeriodStart} — no state/location
 * comparison logic is introduced.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TaxCalculationServiceImpl implements TaxCalculationService {

    private static final BigDecimal ONE_HUNDRED =
            BigDecimal.valueOf(100);

    private static final int MONEY_SCALE = 2;

    private final TaxCalculationRepository taxCalculationRepository;

    private final BillingSnapshotRepository billingSnapshotRepository;

    private final BillingScheduleRepository billingScheduleRepository;

    private final TaxConfigurationRepository taxConfigurationRepository;

    private final TaxRegionMasterRepository taxRegionMasterRepository;

    private final BillingConfigurationService billingConfigurationService;

    @Override
    public TaxCalculationResponseDto calculateTax(
            UUID billingSnapshotId
    ) {

        BillingSnapshot snapshot =
                billingSnapshotRepository.findById(
                                billingSnapshotId
                        )
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "Billing snapshot could not be found."
                                )
                        );

        if (taxCalculationRepository
                .existsByBillingSnapshotId(
                        billingSnapshotId
                )) {

            throw new GlobalExceptionHandler
                    .DuplicateResourceException(
                    "Tax calculation has already been completed for this billing snapshot."
            );
        }

        if (snapshot.getStatus()
                != BillingSnapshotStatus.READY_FOR_TAX) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Tax calculation cannot be started because this billing snapshot is not ready for tax calculation."
            );
        }

        if (snapshot.getTaxRegionId() == null) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Tax region is not configured for this billing snapshot."
            );
        }

        /*
         * Resolve the tax configuration using:
         *
         * Tax Region
         * +
         * Billing Period
         */
        List<TaxConfiguration> configurations =
                taxConfigurationRepository
                        .findApplicableConfigurations(
                                snapshot.getTaxRegionId(),
                                snapshot.getBillingPeriodStart()
                        );

        if (configurations.isEmpty()) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    "No active tax configuration is available for the selected tax region and billing period."
            );
        }

        TaxConfiguration configuration =
                configurations.get(0);

        /*
         * Determine transaction relationship.
         */
        TaxApplicabilityType transactionApplicability =
                resolveApplicability(snapshot);

        snapshot.setStatus(
                BillingSnapshotStatus.IN_TAX
        );

        BigDecimal taxableAmount =
                snapshot.getTotalAmount();

        if (taxableAmount == null) {
            taxableAmount = BigDecimal.ZERO;
        }

        TaxCalculation calculation =
                TaxCalculation.builder()
                        .billingSnapshotId(
                                snapshot.getId()
                        )
                        .taxRegionId(
                                snapshot.getTaxRegionId()
                        )
                        .taxConfigurationId(
                                configuration
                                        .getTaxConfigurationId()
                        )
                        .taxableAmount(
                                taxableAmount
                        )
                        .status(
                                TaxCalculationStatus.CALCULATED
                        )
                        .calculatedAt(
                                LocalDateTime.now()
                        )
                        .build();

        BigDecimal totalTax =
                BigDecimal.ZERO;

        /*
         * Dynamically process all configured components.
         *
         * No CGST/SGST/IGST code exists here.
         */
        for (
                TaxConfigurationComponent configuredComponent
                : configuration.getComponents()
        ) {

            if (!Boolean.TRUE.equals(
                    configuredComponent.getIsActive()
            )) {
                continue;
            }

            if (!isApplicable(
                    configuredComponent,
                    transactionApplicability
            )) {
                continue;
            }

            BigDecimal taxAmount =
                    calculateComponent(
                            taxableAmount,
                            configuredComponent.getTaxRate()
                    );

            TaxCalculationComponent calculatedComponent =
                    TaxCalculationComponent.builder()
                            .taxCalculation(calculation)
                            .taxTypeId(
                                    configuredComponent
                                            .getTaxType()
                                            .getTaxTypeId()
                            )
                            .taxTypeCode(
                                    configuredComponent
                                            .getTaxType()
                                            .getTaxTypeCode()
                            )
                            .taxTypeName(
                                    configuredComponent
                                            .getTaxType()
                                            .getTaxTypeName()
                            )
                            .appliedRate(
                                    configuredComponent
                                            .getTaxRate()
                            )
                            .taxAmount(taxAmount)
                            .applicabilityType(
                                    configuredComponent
                                            .getApplicabilityType()
                            )
                            .build();

            calculation.getComponents()
                    .add(calculatedComponent);

            totalTax =
                    totalTax.add(taxAmount);
        }

        /*
         * If configuration contains components but none apply,
         * tax is legitimately zero.
         *
         * If your business wants this to be an error,
         * change this validation later.
         */
        calculation.setTotalTaxAmount(totalTax);

        calculation.setGrandTotal(
                taxableAmount.add(totalTax)
        );

        TaxCalculation saved;

        try {

            saved =
                    taxCalculationRepository.save(
                            calculation
                    );

        } catch (DataIntegrityViolationException ex) {

            throw new GlobalExceptionHandler
                    .DuplicateResourceException(
                    "Tax calculation has already been completed for this billing snapshot."
            );
        }

        snapshot.setStatus(
                BillingSnapshotStatus.TAX_COMPLETED
        );

        billingSnapshotRepository.save(snapshot);

        BillingConfigurationResponseDto
                snapshotConfiguration =
                billingConfigurationService
                        .getBillingConfiguration(
                                snapshot
                                        .getBillingConfigurationId()
                        );

        return mapToResponse(
                saved,
                snapshot,
                snapshotConfiguration
        );
    }

    @Override
    public TaxCalculationResponseDto calculateTaxForSchedule(
            UUID billingScheduleId
    ) {

        BillingSchedule schedule =
                billingScheduleRepository.findById(billingScheduleId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "Billing schedule could not be found."
                                )
                        );

        if (taxCalculationRepository.existsByBillingScheduleId(billingScheduleId)) {
            throw new GlobalExceptionHandler
                    .DuplicateResourceException(
                    "Tax calculation has already been completed for this billing schedule."
            );
        }

        if (schedule.getPeriodStatus() != BillingPeriodStatus.TAX_PENDING) {
            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Tax calculation cannot be started because this billing schedule is not in TAX_PENDING status."
            );
        }

        if (!Boolean.TRUE.equals(schedule.getIsActive())) {
            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Tax calculation cannot be started because this billing schedule is inactive."
            );
        }

        BillingConfiguration configuration = schedule.getBillingConfiguration();
        if (configuration == null) {
            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Billing configuration is not associated with this billing schedule."
            );
        }

        if (configuration.getTaxRegion() == null) {
            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Tax region is not configured for this billing configuration."
            );
        }

        List<TaxConfiguration> configurations =
                taxConfigurationRepository
                        .findApplicableConfigurations(
                                configuration.getTaxRegion().getTaxRegionId(),
                                schedule.getPeriodStartDate()
                        );

        if (configurations.isEmpty()) {
            throw new GlobalExceptionHandler
                    .ValidationException(
                    "No active tax configuration is available for the selected tax region and billing period."
            );
        }

        TaxConfiguration taxConfiguration = configurations.get(0);

        TaxApplicabilityType transactionApplicability = resolveApplicabilityForSchedule(schedule, configuration);

        BigDecimal taxableAmount = schedule.getBillingAmount();
        if (taxableAmount == null) {
            taxableAmount = BigDecimal.ZERO;
        }

        TaxCalculation calculation =
                TaxCalculation.builder()
                        .billingScheduleId(
                                schedule.getBillingScheduleId()
                        )
                        .taxRegionId(
                                configuration.getTaxRegion().getTaxRegionId()
                        )
                        .taxConfigurationId(
                                taxConfiguration.getTaxConfigurationId()
                        )
                        .taxableAmount(
                                taxableAmount
                        )
                        .status(
                                TaxCalculationStatus.CALCULATED
                        )
                        .calculatedAt(
                                LocalDateTime.now()
                        )
                        .build();

        BigDecimal totalTax = BigDecimal.ZERO;

        log.info("Tax Calculation Diagnostics for BillingSchedule: {}", billingScheduleId);
        log.info("Tax Configuration ID: {}", taxConfiguration.getTaxConfigurationId());
        log.info("Tax Configuration Components Size: {}", taxConfiguration.getComponents().size());
        log.info("Transaction Applicability: {}", transactionApplicability);

        for (TaxConfigurationComponent configuredComponent
                : taxConfiguration.getComponents()) {

            boolean isActive = Boolean.TRUE.equals(configuredComponent.getIsActive());
            boolean applicable = isActive && isApplicable(
                    configuredComponent,
                    transactionApplicability
            );

            log.info("Component: {} | active={} | rate={} | applicability={} | applicable={}",
                    configuredComponent.getTaxType() != null ? configuredComponent.getTaxType().getTaxTypeCode() : "NULL",
                    isActive,
                    configuredComponent.getTaxRate(),
                    configuredComponent.getApplicabilityType(),
                    applicable
            );

            if (!isActive) {
                continue;
            }

            if (!applicable) {
                continue;
            }

            BigDecimal taxAmount =
                    calculateComponent(
                            taxableAmount,
                            configuredComponent.getTaxRate()
                    );

            TaxCalculationComponent calculatedComponent =
                    TaxCalculationComponent.builder()
                            .taxCalculation(calculation)
                            .taxTypeId(
                                    configuredComponent
                                            .getTaxType()
                                            .getTaxTypeId()
                            )
                            .taxTypeCode(
                                    configuredComponent
                                            .getTaxType()
                                            .getTaxTypeCode()
                            )
                            .taxTypeName(
                                    configuredComponent
                                            .getTaxType()
                                            .getTaxTypeName()
                            )
                            .appliedRate(
                                    configuredComponent
                                            .getTaxRate()
                            )
                            .taxAmount(taxAmount)
                            .applicabilityType(
                                    configuredComponent
                                            .getApplicabilityType()
                            )
                            .build();

            calculation.getComponents().add(calculatedComponent);
            totalTax = totalTax.add(taxAmount);
        }

        calculation.setTotalTaxAmount(totalTax);
        calculation.setGrandTotal(taxableAmount.add(totalTax));

        if (calculation.getComponents().isEmpty()) {
            if (taxConfiguration.getComponents().isEmpty()) {
                throw new GlobalExceptionHandler
                        .ValidationException(
                        "No active tax components are configured for the selected Tax Configuration."
                );
            } else {
                throw new GlobalExceptionHandler
                        .ValidationException(
                        "Unable to determine applicable tax components because source and destination jurisdictions are not available."
                );
            }
        }

        TaxCalculation saved;

        try {
            saved = taxCalculationRepository.save(calculation);
        } catch (DataIntegrityViolationException ex) {
            throw new GlobalExceptionHandler
                    .DuplicateResourceException(
                    "Tax calculation has already been completed for this billing schedule."
            );
        }

        schedule.setPeriodStatus(BillingPeriodStatus.TAX_CALCULATED);
        schedule.setTaxStatus(BillingPeriodStatus.TAX_CALCULATED);
        billingScheduleRepository.save(schedule);

        BillingConfigurationResponseDto configurationDto =
                billingConfigurationService
                        .getBillingConfiguration(
                                configuration.getBillingConfigurationId()
                        );

        return mapToResponseForSchedule(
                saved,
                schedule,
                configurationDto
        );
    }

    @Override
    @Transactional(readOnly = true)
    public TaxCalculationResponseDto
    getTaxCalculationBySnapshotId(
            UUID billingSnapshotId
    ) {

        BillingSnapshot snapshot =
                billingSnapshotRepository.findById(
                                billingSnapshotId
                        )
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "Billing snapshot could not be found."
                                )
                        );

        TaxCalculation calculation =
                taxCalculationRepository
                        .findByBillingSnapshotId(
                                billingSnapshotId
                        )
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "No tax calculation has been completed for this billing snapshot."
                                )
                        );

        BillingConfigurationResponseDto
                configuration =
                billingConfigurationService
                        .getBillingConfiguration(
                                snapshot
                                        .getBillingConfigurationId()
                        );

        return mapToResponse(
                calculation,
                snapshot,
                configuration
        );
    }

    /**
     * Determines the transaction relationship from the
     * snapshot's source and destination jurisdictions.
     */
    private TaxApplicabilityType resolveApplicability(
            BillingSnapshot snapshot
    ) {

        String source =
                normalize(
                        snapshot
                                .getSourceTaxJurisdictionCode()
                );

        String destination =
                normalize(
                        snapshot
                                .getDestinationTaxJurisdictionCode()
                );

        /*
         * If no jurisdiction comparison is required/available,
         * ALL is used.
         */
        if (source == null || destination == null) {

            return TaxApplicabilityType.ALL;
        }

        if (source.equalsIgnoreCase(destination)) {

            return TaxApplicabilityType.SAME_JURISDICTION;
        }

        return TaxApplicabilityType.DIFFERENT_JURISDICTION;
    }

    /**
     * Determines the transaction relationship for Fixed Price/Recurring billing.
     * Uses the same business logic as T&M BillingSnapshot:
     * - Source jurisdiction: BillingConfiguration.taxRegion.taxRegionCode (seller's country code)
     * - Destination jurisdiction: Resolve project location to TaxRegion, then use taxRegionCode
     *
     * This ensures we compare country codes (e.g., "IN" == "IN") rather than comparing
     * a code with a location name (e.g., "IN" vs "India").
     */
    private TaxApplicabilityType resolveApplicabilityForSchedule(
            BillingSchedule schedule,
            BillingConfiguration configuration
    ) {

        String source = null;
        String destination = null;

        // Source jurisdiction: Seller's location (from TaxRegion code)
        if (configuration.getTaxRegion() != null) {
            source = normalize(configuration.getTaxRegion().getTaxRegionCode());
        }

        // Destination jurisdiction: Resolve project location to TaxRegion, then get its code
        if (configuration.getProject() != null && configuration.getProject().getPrimaryLocation() != null) {
            String projectLocationName = normalize(configuration.getProject().getPrimaryLocation());
            destination = taxRegionMasterRepository.findByTaxRegionNameIgnoreCase(projectLocationName)
                    .map(taxRegion -> normalize(taxRegion.getTaxRegionCode()))
                    .orElse(null);
        }

        log.info("Jurisdiction Resolution for Schedule {}: source (TaxRegion code)={}, destination (resolved from Project Location)={}",
                schedule.getBillingScheduleId(), source, destination);

        /*
         * If no jurisdiction comparison is required/available,
         * ALL is used.
         */
        if (source == null || destination == null) {
            log.warn("Cannot determine source/destination jurisdictions for schedule {}. Source: {}, Destination: {}. Using ALL applicability.",
                    schedule.getBillingScheduleId(), source, destination);
            return TaxApplicabilityType.ALL;
        }

        if (source.equalsIgnoreCase(destination)) {
            log.info("Jurisdictions are SAME: {} == {}", source, destination);
            return TaxApplicabilityType.SAME_JURISDICTION;
        }

        log.info("Jurisdictions are DIFFERENT: {} != {}", source, destination);
        return TaxApplicabilityType.DIFFERENT_JURISDICTION;
    }

    private boolean isApplicable(
            TaxConfigurationComponent component,
            TaxApplicabilityType transactionApplicability
    ) {

        TaxApplicabilityType configured =
                component.getApplicabilityType();

        if (configured ==
                TaxApplicabilityType.ALL) {

            return true;
        }

        return configured ==
                transactionApplicability;
    }

    private BigDecimal calculateComponent(
            BigDecimal taxableAmount,
            BigDecimal rate
    ) {

        return taxableAmount
                .multiply(rate)
                .divide(
                        ONE_HUNDRED,
                        MONEY_SCALE,
                        RoundingMode.HALF_UP
                );
    }

    private String normalize(String value) {

        if (value == null) {
            return null;
        }

        String normalized =
                value.trim();

        return normalized.isEmpty()
                ? null
                : normalized.toUpperCase();
    }

    private TaxCalculationResponseDto mapToResponse(
            TaxCalculation calculation,
            BillingSnapshot snapshot,
            BillingConfigurationResponseDto configuration
    ) {

        List<TaxCalculationComponentResponseDto>
                components =
                calculation.getComponents()
                        .stream()
                        .map(component ->
                                TaxCalculationComponentResponseDto
                                        .builder()
                                        .taxCalculationComponentId(
                                                component
                                                        .getTaxCalculationComponentId()
                                        )
                                        .taxTypeId(
                                                component
                                                        .getTaxTypeId()
                                        )
                                        .taxTypeCode(
                                                component
                                                        .getTaxTypeCode()
                                        )
                                        .taxTypeName(
                                                component
                                                        .getTaxTypeName()
                                        )
                                        .appliedRate(
                                                component
                                                        .getAppliedRate()
                                        )
                                        .taxAmount(
                                                component
                                                        .getTaxAmount()
                                        )
                                        .applicabilityType(
                                                component
                                                        .getApplicabilityType()
                                        )
                                        .build()
                        )
                        .toList();

        return TaxCalculationResponseDto.builder()
                .taxCalculationId(
                        calculation.getTaxCalculationId()
                )
                .billingSnapshotId(
                        calculation.getBillingSnapshotId()
                )
                .snapshotNumber(
                        snapshot.getSnapshotNumber()
                )
                .projectName(
                        configuration.getProjectName()
                )
                .clientName(
                        configuration.getClientName()
                )
                .billingPeriodStart(
                        snapshot.getBillingPeriodStart()
                )
                .billingPeriodEnd(
                        snapshot.getBillingPeriodEnd()
                )
                .currencyCode(
                        configuration.getCurrencyCode()
                )
                .snapshotStatus(
                        snapshot.getStatus()
                )
                .taxRegionName(
                        configuration.getTaxRegionName()
                )
                .taxRegionCode(
                        configuration.getTaxRegionCode()
                )
                .taxRegionId(
                        calculation.getTaxRegionId()
                )
                .taxConfigurationId(
                        calculation
                                .getTaxConfigurationId()
                )
                .taxableAmount(
                        calculation.getTaxableAmount()
                )
                .components(components)
                .totalTaxAmount(
                        calculation.getTotalTaxAmount()
                )
                .grandTotal(
                        calculation.getGrandTotal()
                )
                .status(
                        calculation.getStatus()
                )
                .calculatedAt(
                        calculation.getCalculatedAt()
                )
                .build();
    }

    private TaxCalculationResponseDto mapToResponseForSchedule(
            TaxCalculation calculation,
            BillingSchedule schedule,
            BillingConfigurationResponseDto configuration
    ) {

        List<TaxCalculationComponentResponseDto>
                components =
                calculation.getComponents()
                        .stream()
                        .map(component ->
                                TaxCalculationComponentResponseDto
                                        .builder()
                                        .taxCalculationComponentId(
                                                component
                                                        .getTaxCalculationComponentId()
                                        )
                                        .taxTypeId(
                                                component
                                                        .getTaxTypeId()
                                        )
                                        .taxTypeCode(
                                                component
                                                        .getTaxTypeCode()
                                        )
                                        .taxTypeName(
                                                component
                                                        .getTaxTypeName()
                                        )
                                        .appliedRate(
                                                component
                                                        .getAppliedRate()
                                        )
                                        .taxAmount(
                                                component
                                                        .getTaxAmount()
                                        )
                                        .applicabilityType(
                                                component
                                                        .getApplicabilityType()
                                        )
                                        .build()
                        )
                        .toList();

        return TaxCalculationResponseDto.builder()
                .taxCalculationId(
                        calculation.getTaxCalculationId()
                )
                .billingSnapshotId(null)
                .billingScheduleId(
                        calculation.getBillingScheduleId()
                )
                .snapshotNumber(null)
                .projectName(
                        configuration.getProjectName()
                )
                .clientName(
                        configuration.getClientName()
                )
                .billingPeriodStart(
                        schedule.getPeriodStartDate()
                )
                .billingPeriodEnd(
                        schedule.getPeriodEndDate()
                )
                .currencyCode(
                        configuration.getCurrencyCode()
                )
                .snapshotStatus(null)
                .taxRegionName(
                        configuration.getTaxRegionName()
                )
                .taxRegionCode(
                        configuration.getTaxRegionCode()
                )
                .taxRegionId(
                        calculation.getTaxRegionId()
                )
                .taxConfigurationId(
                        calculation
                                .getTaxConfigurationId()
                )
                .taxableAmount(
                        calculation.getTaxableAmount()
                )
                .components(components)
                .totalTaxAmount(
                        calculation.getTotalTaxAmount()
                )
                .grandTotal(
                        calculation.getGrandTotal()
                )
                .status(
                        calculation.getStatus()
                )
                .calculatedAt(
                        calculation.getCalculatedAt()
                )
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TaxCalculationResponseDto getTaxCalculationByScheduleId(
            UUID billingScheduleId) {

        BillingSchedule schedule =
                billingScheduleRepository.findById(billingScheduleId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "Billing schedule could not be found."
                                )
                        );

        TaxCalculation calculation =
                taxCalculationRepository
                        .findByBillingScheduleId(billingScheduleId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "No tax calculation has been completed for this billing schedule."
                                )
                        );

        BillingConfigurationResponseDto configuration =
                billingConfigurationService
                        .getBillingConfiguration(
                                schedule.getBillingConfiguration()
                                        .getBillingConfigurationId()
                        );

        return mapToResponseForSchedule(
                calculation,
                schedule,
                configuration
        );
    }

}
