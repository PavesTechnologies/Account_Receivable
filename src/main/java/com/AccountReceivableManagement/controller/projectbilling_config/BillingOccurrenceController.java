package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingOccurrenceResponseDto;
import com.AccountReceivableManagement.dto.tax_calculation.TaxCalculationResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingRecurringConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingRecurringConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.repo.tax_calculation.TaxCalculationRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingConfigurationService;
import com.AccountReceivableManagement.service_interface.tax_calculation.TaxCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/billing-occurrences")
@RequiredArgsConstructor
public class BillingOccurrenceController {

    private final BillingScheduleRepository billingScheduleRepository;
    private final BillingConfigurationRepository billingConfigurationRepository;
    private final BillingRecurringConfigurationRepository billingRecurringConfigurationRepository;
    private final TaxCalculationRepository taxCalculationRepository;
    private final BillingConfigurationService billingConfigurationService;
    private final TaxCalculationService taxCalculationService;

    @GetMapping("/{occurrenceId}")
    public ResponseEntity<BillingOccurrenceResponseDto> getOccurrence(
            @PathVariable UUID occurrenceId) {

        BillingSchedule schedule = billingScheduleRepository.findById(occurrenceId)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                        "Billing occurrence not found"));

        return ResponseEntity.ok(mapToResponse(schedule));
    }

    @GetMapping
    public ResponseEntity<List<BillingOccurrenceResponseDto>> getOccurrences(
            @RequestParam(required = false) UUID billingConfigurationId,
            @RequestParam(required = false) UUID recurringConfigurationId,
            @RequestParam(required = false) BillingPeriodStatus periodStatus,
            @RequestParam(required = false) BillingPeriodStatus taxStatus,
            @RequestParam(required = false) LocalDate billingDateBefore,
            @RequestParam(required = false) LocalDate billingDateAfter) {

        List<BillingSchedule> schedules;

        if (billingConfigurationId != null) {
            BillingConfiguration configuration = billingConfigurationRepository
                    .findById(billingConfigurationId)
                    .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                            "Billing configuration not found"));

            if (periodStatus != null && taxStatus != null) {
                schedules = billingScheduleRepository
                        .findByBillingConfigurationAndPeriodStatusAndTaxStatusAndIsActiveTrue(
                                configuration, periodStatus, taxStatus);
            } else if (periodStatus != null) {
                schedules = billingScheduleRepository.findByBillingConfigurationAndPeriodStatusAndIsActiveTrue(
                        configuration, periodStatus);
            } else if (taxStatus != null) {
                schedules = billingScheduleRepository.findByBillingConfigurationAndTaxStatusAndIsActiveTrue(
                        configuration, taxStatus);
            } else {
                schedules = billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(
                        configuration);
            }
        } else if (recurringConfigurationId != null) {
            BillingRecurringConfiguration recurring = billingRecurringConfigurationRepository
                    .findById(recurringConfigurationId)
                    .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                            "Recurring configuration not found"));

            if (periodStatus != null && taxStatus != null) {
                schedules = billingScheduleRepository
                        .findByRecurringConfigurationAndPeriodStatusAndTaxStatusAndIsActiveTrue(
                                recurring, periodStatus, taxStatus);
            } else if (periodStatus != null) {
                schedules = billingScheduleRepository.findByRecurringConfigurationAndPeriodStatusAndIsActiveTrue(
                        recurring, periodStatus);
            } else if (taxStatus != null) {
                schedules = billingScheduleRepository.findByRecurringConfigurationAndTaxStatusAndIsActiveTrue(
                        recurring, taxStatus);
            } else {
                schedules = billingScheduleRepository.findByRecurringConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(
                        recurring);
            }
        } else if (periodStatus != null && taxStatus != null) {
            schedules = billingScheduleRepository.findByPeriodStatusAndTaxStatusAndIsActiveTrue(
                    periodStatus, taxStatus);
        } else if (periodStatus != null) {
            schedules = billingScheduleRepository.findByPeriodStatusAndIsActiveTrue(periodStatus);
        } else if (taxStatus != null && billingDateBefore != null) {
            schedules = billingScheduleRepository.findByBillingDateBeforeAndTaxStatusAndIsActiveTrue(
                    billingDateBefore, taxStatus);
        } else if (taxStatus != null) {
            schedules = billingScheduleRepository.findByTaxStatusAndIsActiveTrue(taxStatus);
        } else {
            throw new GlobalExceptionHandler.ValidationException(
                    "At least one filter parameter is required");
        }

        // Apply date range filter if provided
        if (billingDateAfter != null) {
            schedules = schedules.stream()
                    .filter(s -> s.getBillingDate() != null && !s.getBillingDate().isBefore(billingDateAfter))
                    .collect(Collectors.toList());
        }

        if (billingDateBefore != null && taxStatus == null) {
            schedules = schedules.stream()
                    .filter(s -> s.getBillingDate() != null && !s.getBillingDate().isAfter(billingDateBefore))
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(schedules.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{occurrenceId}/tax-calculation")
    public ResponseEntity<TaxCalculationResponseDto> getTaxCalculation(
            @PathVariable UUID occurrenceId) {

        BillingSchedule schedule = billingScheduleRepository.findById(occurrenceId)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                        "Billing occurrence not found"));

        return taxCalculationRepository.findByBillingScheduleId(occurrenceId)
                .map(taxCalculation -> ResponseEntity.ok(
                        taxCalculationService.getTaxCalculationByScheduleId(occurrenceId)))
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                        "Tax calculation not found for this billing occurrence"));
    }

    @PostMapping("/{occurrenceId}/calculate-tax")
    public ResponseEntity<TaxCalculationResponseDto> calculateTax(
            @PathVariable UUID occurrenceId) {

        BillingSchedule schedule = billingScheduleRepository.findById(occurrenceId)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                        "Billing occurrence not found"));

        if (schedule.getPeriodStatus() != BillingPeriodStatus.TAX_PENDING) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Tax calculation can only be triggered for occurrences in TAX_PENDING status");
        }

        return ResponseEntity.ok(taxCalculationService.calculateTaxForSchedule(occurrenceId));
    }

    private BillingOccurrenceResponseDto mapToResponse(BillingSchedule schedule) {
        BillingOccurrenceResponseDto.BillingOccurrenceResponseDtoBuilder builder = BillingOccurrenceResponseDto.builder()
                .billingScheduleId(schedule.getBillingScheduleId())
                .periodNumber(schedule.getPeriodNumber())
                .periodStartDate(schedule.getPeriodStartDate())
                .periodEndDate(schedule.getPeriodEndDate())
                .billingDate(schedule.getBillingDate())
                .billingAmount(schedule.getBillingAmount())
                .scheduleType(schedule.getScheduleType())
                .isPartialPeriod(schedule.getIsPartialPeriod())
                .periodStatus(schedule.getPeriodStatus())
                .taxStatus(schedule.getTaxStatus())
                .isInvoiced(schedule.getIsInvoiced())
                .invoiceDate(schedule.getInvoiceDate())
                .remarks(schedule.getRemarks())
                .isActive(schedule.getIsActive())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt());

        if (schedule.getBillingConfiguration() != null) {
            builder.billingConfigurationId(schedule.getBillingConfiguration().getBillingConfigurationId());
        }

        if (schedule.getRecurringConfiguration() != null) {
            builder.recurringConfigurationId(schedule.getRecurringConfiguration().getRecurringConfigurationId());
        }

        // Include tax calculation details if available
        taxCalculationRepository.findByBillingScheduleId(schedule.getBillingScheduleId())
                .ifPresent(taxCalculation -> {
                    builder.taxCalculationId(taxCalculation.getTaxCalculationId())
                            .taxCalculationStatus(taxCalculation.getStatus())
                            .taxableAmount(taxCalculation.getTaxableAmount())
                            .totalTaxAmount(taxCalculation.getTotalTaxAmount())
                            .grandTotal(taxCalculation.getGrandTotal())
                            .taxCalculatedAt(taxCalculation.getCalculatedAt())
                            .taxComponents(taxCalculation.getComponents().stream()
                                    .map(component -> com.AccountReceivableManagement.dto.tax_calculation.TaxCalculationComponentResponseDto.builder()
                                            .taxCalculationComponentId(component.getTaxCalculationComponentId())
                                            .taxTypeId(component.getTaxTypeId())
                                            .taxTypeCode(component.getTaxTypeCode())
                                            .taxTypeName(component.getTaxTypeName())
                                            .appliedRate(component.getAppliedRate())
                                            .taxAmount(component.getTaxAmount())
                                            .applicabilityType(component.getApplicabilityType())
                                            .build())
                                    .collect(Collectors.toList()));
                });

        // Include configuration details if available
        if (schedule.getBillingConfiguration() != null) {
            try {
                var configDto = billingConfigurationService.getBillingConfiguration(
                        schedule.getBillingConfiguration().getBillingConfigurationId());
                builder.projectName(configDto.getProjectName())
                        .clientName(configDto.getClientName())
                        .currencyCode(configDto.getCurrencyCode())
                        .taxRegionName(configDto.getTaxRegionName())
                        .taxRegionCode(configDto.getTaxRegionCode());
            } catch (Exception e) {
                // Ignore if configuration details cannot be retrieved
            }
        }

        return builder.build();
    }
}
