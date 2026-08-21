package com.AccountReceivableManagement.service_Imple.tax_calculation;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.tax_calculation.TaxCalculationResponseDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRateConfiguration;
import com.AccountReceivableManagement.entity.tax_calculation.TaxCalculation;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxCalculationStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRateConfigurationRepository;
import com.AccountReceivableManagement.repo.tax_calculation.TaxCalculationRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingConfigurationService;
import com.AccountReceivableManagement.service_interface.tax_calculation.TaxCalculationService;
import lombok.RequiredArgsConstructor;
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
 * Phase 1 {@link TaxRateConfiguration} resolved for the snapshot's own
 * {@code taxRegionId} and {@code billingPeriodStart} — no state/location
 * comparison logic is introduced.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TaxCalculationServiceImpl implements TaxCalculationService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int MONETARY_SCALE = 2;

    private final TaxCalculationRepository taxCalculationRepository;
    private final BillingSnapshotRepository billingSnapshotRepository;
    private final TaxRateConfigurationRepository taxRateConfigurationRepository;
    private final BillingConfigurationService billingConfigurationService;

    @Override
    public TaxCalculationResponseDto calculateTax(UUID billingSnapshotId) {

        BillingSnapshot snapshot = billingSnapshotRepository.findById(billingSnapshotId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Billing snapshot could not be found."));

        if (taxCalculationRepository.existsByBillingSnapshotId(billingSnapshotId)) {
            throw new GlobalExceptionHandler.DuplicateResourceException(
                    "Tax calculation has already been completed for this billing snapshot.");
        }

        if (snapshot.getStatus() != BillingSnapshotStatus.READY_TO_TAX) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Tax calculation cannot be started because this billing snapshot is not ready for tax calculation.");
        }

        if (snapshot.getTaxRegionId() == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Tax calculation cannot proceed because no tax region is configured for this billing snapshot.");
        }

        List<TaxRateConfiguration> applicableConfigurations = taxRateConfigurationRepository
                .findApplicableConfigurations(snapshot.getTaxRegionId(), snapshot.getBillingPeriodStart());

        if (applicableConfigurations.isEmpty()) {
            throw new GlobalExceptionHandler.ValidationException(
                    "No active tax configuration is available for the selected tax region and billing period.");
        }

        TaxRateConfiguration configuration = applicableConfigurations.get(0);

        // A rate that is null or exactly zero is treated as "not applicable" — a blank
        // component is never coerced to zero for calculation purposes (see calculation
        // below), but an explicit zero rate is likewise not enough to make a component
        // applicable, matching the intended CGST>0/SGST>0 vs IGST>0 business rule.
        boolean hasCgstSgst = isPositive(configuration.getCgstRate()) && isPositive(configuration.getSgstRate());
        boolean hasIgst = isPositive(configuration.getIgstRate());

        if (hasCgstSgst == hasIgst) {
            // Either neither component is configured (shouldn't happen — Phase 1 requires
            // at least one) or a mixed/ambiguous combination (e.g. CGST alone, or CGST+IGST
            // together) that Phase 1's "at least one component" rule does not itself reject.
            throw new GlobalExceptionHandler.ValidationException(
                    "The resolved tax configuration has an invalid combination of tax components. " +
                            "Configure either both CGST and SGST, or IGST alone.");
        }

        // Calculation starts: the snapshot is now committed to this attempt.
        snapshot.setStatus(BillingSnapshotStatus.IN_TAX);

        BigDecimal taxableAmount = snapshot.getTotalAmount();

        BigDecimal cgstRate = null;
        BigDecimal cgstAmount = null;
        BigDecimal sgstRate = null;
        BigDecimal sgstAmount = null;
        BigDecimal igstRate = null;
        BigDecimal igstAmount = null;

        if (hasCgstSgst) {
            cgstRate = configuration.getCgstRate();
            cgstAmount = calculateComponent(taxableAmount, cgstRate);
            sgstRate = configuration.getSgstRate();
            sgstAmount = calculateComponent(taxableAmount, sgstRate);
        } else {
            igstRate = configuration.getIgstRate();
            igstAmount = calculateComponent(taxableAmount, igstRate);
        }

        BigDecimal totalTaxAmount = nullToZero(cgstAmount)
                .add(nullToZero(sgstAmount))
                .add(nullToZero(igstAmount));

        BigDecimal grandTotal = taxableAmount.add(totalTaxAmount);

        TaxCalculation taxCalculation = TaxCalculation.builder()
                .billingSnapshotId(snapshot.getId())
                .taxRegionId(snapshot.getTaxRegionId())
                .taxRateConfigurationId(configuration.getTaxRateConfigurationId())
                .taxableAmount(taxableAmount)
                .cgstRate(cgstRate)
                .cgstAmount(cgstAmount)
                .sgstRate(sgstRate)
                .sgstAmount(sgstAmount)
                .igstRate(igstRate)
                .igstAmount(igstAmount)
                .totalTaxAmount(totalTaxAmount)
                .grandTotal(grandTotal)
                .status(TaxCalculationStatus.CALCULATED)
                .calculatedAt(LocalDateTime.now())
                .build();

        TaxCalculation savedCalculation;
        try {
            savedCalculation = taxCalculationRepository.save(taxCalculation);
        } catch (DataIntegrityViolationException ex) {
            throw new GlobalExceptionHandler.DuplicateResourceException(
                    "Tax calculation has already been completed for this billing snapshot.");
        }

        snapshot.setStatus(BillingSnapshotStatus.TAX_COMPLETED);
        billingSnapshotRepository.save(snapshot);

        BillingConfigurationResponseDto snapshotConfiguration =
                billingConfigurationService.getBillingConfiguration(snapshot.getBillingConfigurationId());

        return mapToResponse(savedCalculation, snapshot, snapshotConfiguration);
    }

    @Override
    public TaxCalculationResponseDto getTaxCalculationBySnapshotId(UUID billingSnapshotId) {

        BillingSnapshot snapshot = billingSnapshotRepository.findById(billingSnapshotId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Billing snapshot could not be found."));

        TaxCalculation taxCalculation = taxCalculationRepository.findByBillingSnapshotId(billingSnapshotId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException(
                                "No tax calculation has been completed for this billing snapshot."));

        BillingConfigurationResponseDto snapshotConfiguration =
                billingConfigurationService.getBillingConfiguration(snapshot.getBillingConfigurationId());

        return mapToResponse(taxCalculation, snapshot, snapshotConfiguration);
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * rate is a percentage (e.g. 9.0000 for 9%); amount = taxableAmount x rate / 100,
     * rounded HALF_UP to 2 decimal places. No existing monetary rounding convention
     * exists elsewhere in the codebase, so this is the convention introduced here.
     */
    private BigDecimal calculateComponent(BigDecimal taxableAmount, BigDecimal rate) {
        return taxableAmount.multiply(rate)
                .divide(ONE_HUNDRED, MONETARY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private TaxCalculationResponseDto mapToResponse(TaxCalculation taxCalculation, BillingSnapshot snapshot,
            BillingConfigurationResponseDto configuration) {

        return TaxCalculationResponseDto.builder()
                .taxCalculationId(taxCalculation.getTaxCalculationId())
                .billingSnapshotId(taxCalculation.getBillingSnapshotId())
                .snapshotNumber(snapshot.getSnapshotNumber())
                .projectName(configuration.getProjectName())
                .clientName(configuration.getClientName())
                .billingPeriodStart(snapshot.getBillingPeriodStart())
                .billingPeriodEnd(snapshot.getBillingPeriodEnd())
                .currencyCode(configuration.getCurrencyCode())
                .snapshotStatus(snapshot.getStatus())
                .taxRegionName(configuration.getTaxRegionName())
                .taxRegionCode(configuration.getTaxRegionCode())
                .taxRegionId(taxCalculation.getTaxRegionId())
                .taxRateConfigurationId(taxCalculation.getTaxRateConfigurationId())
                .taxableAmount(taxCalculation.getTaxableAmount())
                .cgstRate(taxCalculation.getCgstRate())
                .cgstAmount(taxCalculation.getCgstAmount())
                .sgstRate(taxCalculation.getSgstRate())
                .sgstAmount(taxCalculation.getSgstAmount())
                .igstRate(taxCalculation.getIgstRate())
                .igstAmount(taxCalculation.getIgstAmount())
                .totalTaxAmount(taxCalculation.getTotalTaxAmount())
                .grandTotal(taxCalculation.getGrandTotal())
                .status(taxCalculation.getStatus())
                .calculatedAt(taxCalculation.getCalculatedAt())
                .build();
    }
}
