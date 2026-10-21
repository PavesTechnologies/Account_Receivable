package com.AccountReceivableManagement.service_Imple.tax_calculation;

import com.AccountReceivableManagement.dto.recovery.RecoveryResultDto;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.tax_calculation.TaxCalculation;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.repo.tax_calculation.TaxCalculationRepository;
import com.AccountReceivableManagement.security.Actor;
import com.AccountReceivableManagement.security.FinancialAction;
import com.AccountReceivableManagement.security.FinancialActionAuthorizer;
import com.AccountReceivableManagement.service_interface.tax_calculation.TaxCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * BD-07 recovery: an occurrence whose tax calculation no longer matches its
 * configuration's currency or tax region (a re-approved edit) is refused at
 * invoicing (G-12). This lets an authorized operator recalculate its tax -
 * and only in that situation: never for an invoiced occurrence, never for a
 * calculation that still matches (nothing to correct). The old calculation
 * is superseded, not deleted, and the new one goes through the normal tax
 * path with every existing check (approval, billing-type rules, explicit
 * jurisdictions). Requires OPERATIONS_RECOVERY.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxRecalculationRecoveryService {

    static final String RECORD_TYPE = "BILLING_OCCURRENCE";

    private final BillingScheduleRepository billingScheduleRepository;
    private final TaxCalculationRepository taxCalculationRepository;
    private final InvoiceRepository invoiceRepository;
    private final TaxCalculationService taxCalculationService;
    private final FinancialActionAuthorizer authorizer;
    private final PlatformTransactionManager transactionManager;

    public RecoveryResultDto recalculateStaleTax(UUID occurrenceId, String reason) {
        Actor actor = authorizer.requirePermission(FinancialAction.OPERATIONS_RECOVERY);
        if (reason == null || reason.isBlank()) {
            throw new GlobalExceptionHandler.ValidationException("A reason is required for a recovery action.");
        }
        String why = reason.trim().length() > 500 ? reason.trim().substring(0, 500) : reason.trim();

        return new TransactionTemplate(transactionManager).execute(status -> {
            BillingSchedule occurrence = billingScheduleRepository.findByIdForUpdate(occurrenceId)
                    .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                            "Billing occurrence could not be found."));
            if (invoiceRepository.existsByBillingScheduleId(occurrenceId)) {
                return rejected(occurrenceId, occurrence, "The occurrence is already invoiced; its tax is final. "
                        + "Use the invoice correction process instead.");
            }
            TaxCalculation current = taxCalculationRepository.findByBillingScheduleId(occurrenceId).orElse(null);
            if (current == null || occurrence.getPeriodStatus() != BillingPeriodStatus.TAX_CALCULATED) {
                return rejected(occurrenceId, occurrence, "The occurrence has no completed tax calculation to correct.");
            }
            String mismatch = contextMismatch(current, occurrence.getBillingConfiguration());
            if (mismatch == null) {
                return rejected(occurrenceId, occurrence, "The tax calculation matches the configuration's currency "
                        + "and tax region; nothing to recalculate.");
            }

            current.setSupersededAt(LocalDateTime.now());
            current.setSupersededBillingScheduleId(occurrenceId);
            current.setBillingScheduleId(null);
            current.setSupersededBy(actor.label());
            current.setSupersededByUserId(actor.userId());
            current.setSupersededReason(why);
            taxCalculationRepository.saveAndFlush(current);

            occurrence.setPeriodStatus(BillingPeriodStatus.TAX_PENDING);
            occurrence.setTaxStatus(BillingPeriodStatus.TAX_PENDING);
            billingScheduleRepository.saveAndFlush(occurrence);

            // The normal path with all its checks; a failure rolls everything back.
            var recalculated = taxCalculationService.calculateTaxForSchedule(occurrenceId);
            log.warn("[TaxRecalculated] occurrence={} superseded={} reason={} by={}", occurrenceId,
                    current.getTaxCalculationId(), mismatch, actor.label());
            return RecoveryResultDto.builder()
                    .recordId(occurrenceId)
                    .recordType(RECORD_TYPE)
                    .accepted(true)
                    .state(BillingPeriodStatus.TAX_CALCULATED.name())
                    .message("Tax recalculated (" + mismatch + "); previous calculation "
                            + current.getTaxCalculationId() + " kept as superseded. New calculation "
                            + recalculated.getTaxCalculationId() + ".")
                    .build();
        });
    }

    /** What changed since the calculation, or null if it still matches. */
    static String contextMismatch(TaxCalculation calculation, BillingConfiguration configuration) {
        if (configuration == null) {
            return null;
        }
        UUID regionNow = configuration.getTaxRegion() != null ? configuration.getTaxRegion().getTaxRegionId() : null;
        if (calculation.getTaxRegionId() != null && regionNow != null
                && !Objects.equals(calculation.getTaxRegionId(), regionNow)) {
            return "tax region changed";
        }
        String currencyNow = configuration.getCurrency() != null ? configuration.getCurrency().getCurrencyCode() : null;
        if (calculation.getCurrencyCode() != null && currencyNow != null
                && !calculation.getCurrencyCode().equalsIgnoreCase(currencyNow)) {
            return "currency changed";
        }
        return null;
    }

    private static RecoveryResultDto rejected(UUID occurrenceId, BillingSchedule occurrence, String message) {
        return RecoveryResultDto.builder()
                .recordId(occurrenceId)
                .recordType(RECORD_TYPE)
                .accepted(false)
                .state(occurrence.getPeriodStatus() != null ? occurrence.getPeriodStatus().name() : null)
                .message(message)
                .build();
    }
}
