package com.AccountReceivableManagement.service_Imple.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingDataAcquisitionResponseDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingAcquisition;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingAcquisitionStatus;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingAcquisitionRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingDataAcquisitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Phase 2 implementation: fetches active billing configurations
 * and matches the billing acquisition execution record for each configuration's billing period.
 *
 * Status logic:
 *   - For each active BillingConfiguration:
 *     - find the BillingAcquisition record that matches the configured billing period
 *     - if a matching record exists: status = acquisition.status, lastInvoice = acquisition.finalInvoiceId
 *     - if no matching record exists: status = WAITING_FOR_SOURCE_DATA, lastInvoice = null
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BillingDataAcquisitionServiceImpl implements BillingDataAcquisitionService {

    private final BillingConfigurationRepository billingConfigurationRepository;
    private final BillingAcquisitionRepository billingAcquisitionRepository;

    @Override
    public List<BillingDataAcquisitionResponseDto> getActiveConfigurations() {
        // Filter: is_active = 1 (true) — set by the approve() workflow.
        return billingConfigurationRepository.findAllActive()
                .stream()
                .sorted(Comparator.comparing(
                        bc -> bc.getProject().getProjectName(),
                        Comparator.nullsLast(String::compareToIgnoreCase)
                ))
                .map(this::mapToDto)
                .toList();
    }

    // -----------------------------------------------------------------------
    // Private mapping helper
    // -----------------------------------------------------------------------

    private BillingDataAcquisitionResponseDto mapToDto(BillingConfiguration bc) {

        // Project code: use pmsProjectId formatted as PRJ-{id} since
        // no dedicated code column exists on project_master_reference.
        String projectCode = "PRJ-" + bc.getProject().getPmsProjectId();

        // Generation mode: read from invoice_generation_type column
        // (InvoiceGenerationType enum: MANUAL / AUTOMATIC).
        String generationMode = bc.getInvoiceGenerationType() != null
                ? bc.getInvoiceGenerationType().name()
                : null;

        // Match the BillingAcquisition record that matches the configured billing period
        Optional<BillingAcquisition> acquisitionOpt = Optional.empty();
        if (bc.getEffectiveFrom() != null && bc.getEffectiveTo() != null) {
            acquisitionOpt = billingAcquisitionRepository
                    .findByBillingConfiguration_BillingConfigurationIdAndBillingPeriodStartAndBillingPeriodEnd(
                            bc.getBillingConfigurationId(),
                            bc.getEffectiveFrom(),
                            bc.getEffectiveTo()
                    );
        }

        // Fallback to latest record for this configuration if period dates are missing
        if (acquisitionOpt.isEmpty()) {
            acquisitionOpt = billingAcquisitionRepository
                    .findFirstByBillingConfiguration_BillingConfigurationIdOrderByCreatedAtDesc(bc.getBillingConfigurationId());
        }

        String status = BillingAcquisitionStatus.NOT_ACQUIRED.name();
        String lastInvoice = null;

        if (acquisitionOpt.isPresent()) {
            BillingAcquisition acquisition = acquisitionOpt.get();
            status = acquisition.getStatus() != null ? acquisition.getStatus().name() : BillingAcquisitionStatus.NOT_ACQUIRED.name();
            lastInvoice = acquisition.getFinalInvoiceId();
        }

        return BillingDataAcquisitionResponseDto.builder()
                .billingConfigurationId(bc.getBillingConfigurationId())
                .projectId(bc.getProject().getPmsProjectId())
                .projectName(bc.getProject().getProjectName())
                .projectCode(projectCode)
                .clientName(bc.getClient().getClientName())
                .billingType(bc.getBillingType().getBillingTypeName())
                .frequency(bc.getBillingFrequency().getBillingFrequencyName())
                .currency(bc.getCurrency() != null ? bc.getCurrency().getCurrencyCode() : "INR")
                .billingPeriodStart(bc.getEffectiveFrom())
                .billingPeriodEnd(bc.getEffectiveTo())
                .generationMode(generationMode)
                .status(status)
                .lastInvoice(lastInvoice)
                .build();
    }
}
