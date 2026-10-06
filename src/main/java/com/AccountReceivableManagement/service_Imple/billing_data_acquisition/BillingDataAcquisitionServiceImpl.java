package com.AccountReceivableManagement.service_Imple.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingDataAcquisitionResponseDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingAcquisition;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingAcquisitionStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingAcquisitionRepository;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingDataAcquisitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fetches active billing configurations and matches the most recent billing
 * acquisition execution record for each configuration.
 *
 * Status logic (BillingAcquisitionStatus, acquisition-level only — does not
 * reflect downstream BillingSnapshot/tax/invoice lifecycle status):
 *   - For each active BillingConfiguration:
 *     - find the latest BillingAcquisition record for that configuration
 *     - if a record exists: status = acquisition.status, lastInvoice = acquisition.finalInvoiceId,
 *       and billingPeriodStart/End = the actual dates the user selected on Acquire Snapshot
 *       (never the Billing Configuration's own effectiveFrom/effectiveTo validity window)
 *     - if no record exists: status = NOT_ACQUIRED, lastInvoice = null, billing period = null
 *     - unless a BillingSnapshot exists that has no acquisition record of its own
 *       (the snapshot was persisted but recording the acquisition never happened):
 *       status = READY and the billing period/snapshotId come from that snapshot.
 *       This read path is side-effect free; the record itself is reconciled the
 *       next time POST /billing-snapshots is called for that period.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BillingDataAcquisitionServiceImpl implements BillingDataAcquisitionService {

    private final BillingConfigurationRepository billingConfigurationRepository;
    private final BillingAcquisitionRepository billingAcquisitionRepository;
    private final BillingSnapshotRepository billingSnapshotRepository;

    @Override
    @Transactional(readOnly = true)
    public List<BillingDataAcquisitionResponseDto> getActiveConfigurations() {

        return billingConfigurationRepository
                .findByApprovalStatusAndBillingStatus(
                        ApprovalStatus.APPROVED,
                        BillingConfigurationStatus.ACTIVE
                )
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

        // Resolve the most recent BillingAcquisition execution record for this
        // configuration. Its billingPeriodStart/End are the dates the user
        // actually selected on Acquire Snapshot — never the Billing
        // Configuration's own effectiveFrom/effectiveTo validity window.
        Optional<BillingAcquisition> acquisitionOpt = billingAcquisitionRepository
                .findFirstByBillingConfiguration_BillingConfigurationIdOrderByCreatedAtDesc(bc.getBillingConfigurationId());

        // A persisted snapshot whose acquisition record was never written must
        // not be reported as NOT_ACQUIRED, or the console offers to acquire it again.
        Optional<BillingSnapshot> orphanedSnapshotOpt = billingSnapshotRepository
                .findFirstByBillingConfigurationIdOrderByCreatedDateDesc(bc.getBillingConfigurationId())
                .filter(snapshot -> isNewerThanAcquisition(snapshot, acquisitionOpt));

        String status = BillingAcquisitionStatus.NOT_ACQUIRED.name();
        String lastInvoice = null;
        LocalDate billingPeriodStart = null;
        LocalDate billingPeriodEnd = null;
        UUID snapshotId = null;

        if (orphanedSnapshotOpt.isPresent()) {
            BillingSnapshot snapshot = orphanedSnapshotOpt.get();
            // Same status reconciliation records for it (BillingAcquisitionService#recordAcquisitionForSnapshot).
            status = BillingAcquisitionStatus.READY.name();
            billingPeriodStart = snapshot.getBillingPeriodStart();
            billingPeriodEnd = snapshot.getBillingPeriodEnd();
            snapshotId = snapshot.getId();
        } else if (acquisitionOpt.isPresent()) {
            BillingAcquisition acquisition = acquisitionOpt.get();
            status = acquisition.getStatus() != null ? acquisition.getStatus().name() : BillingAcquisitionStatus.NOT_ACQUIRED.name();
            lastInvoice = acquisition.getFinalInvoiceId();
            billingPeriodStart = acquisition.getBillingPeriodStart();
            billingPeriodEnd = acquisition.getBillingPeriodEnd();
            snapshotId = acquisition.getSnapshotId();
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
                .billingPeriodStart(billingPeriodStart)
                .billingPeriodEnd(billingPeriodEnd)
                .generationMode(generationMode)
                .status(status)
                .lastInvoice(lastInvoice)
                .snapshotId(snapshotId)
                .build();
    }

    /**
     * True when {@code snapshot} has no acquisition record of its own: either
     * none exists for the configuration, or the latest one belongs to a
     * different billing period and predates the snapshot.
     */
    private boolean isNewerThanAcquisition(BillingSnapshot snapshot, Optional<BillingAcquisition> acquisitionOpt) {
        if (acquisitionOpt.isEmpty()) {
            return true;
        }
        BillingAcquisition acquisition = acquisitionOpt.get();
        boolean samePeriod = snapshot.getBillingPeriodStart().equals(acquisition.getBillingPeriodStart())
                && snapshot.getBillingPeriodEnd().equals(acquisition.getBillingPeriodEnd());
        return !samePeriod
                && snapshot.getCreatedDate() != null
                && acquisition.getCreatedAt() != null
                && snapshot.getCreatedDate().isAfter(acquisition.getCreatedAt());
    }
}
