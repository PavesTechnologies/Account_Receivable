package com.AccountReceivableManagement.service_Imple.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionSummaryDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingDataAcquisitionResponseDto;
import com.AccountReceivableManagement.dto.centralizeddto.PagedResponse;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingAcquisition;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingAcquisitionStatus;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.integration.billing_data_acquisition.BillingConfigurationIntegrationImpl;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingAcquisitionRepository;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTypeMasterRepository;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingDataAcquisitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BillingDataAcquisitionServiceImpl implements BillingDataAcquisitionService {

    private final BillingConfigurationRepository billingConfigurationRepository;
    private final BillingAcquisitionRepository billingAcquisitionRepository;
    private final BillingSnapshotRepository billingSnapshotRepository;
    private final BillingTypeMasterRepository billingTypeMasterRepository;

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

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<BillingAcquisitionSummaryDto> getAcquisitionOverview(
            BillingType billingType, int page, int size) {

        // Unsorted on purpose: the ordering (project start date DESC, nulls last,
        // id as tiebreaker) is part of the repository query itself.
        Pageable pageable = PageRequest.of(page, size);

        Page<BillingConfiguration> result;
        if (billingType == null) {
            result = billingConfigurationRepository.findAcquisitionOverviewPage(
                    ApprovalStatus.APPROVED, BillingConfigurationStatus.ACTIVE, pageable);
        } else {
            Set<String> billingTypeNames = resolveBillingTypeNames(billingType);
            if (billingTypeNames.isEmpty()) {
                // No master row maps to this billing type, so no configuration can
                // match. Returning an empty page keeps "nothing configured" distinct
                // from an error, and avoids an `IN ()` query.
                log.warn("[BillingDataAcquisition] No billing_type_master row maps to billing type {}; "
                        + "the listing has nothing to return for it.", billingType);
                return PagedResponse.empty(page, size);
            }
            result = billingConfigurationRepository.findAcquisitionOverviewPageByBillingTypeNames(
                    ApprovalStatus.APPROVED, BillingConfigurationStatus.ACTIVE, billingTypeNames, pageable);
        }

        List<BillingAcquisitionSummaryDto> content = result.getContent().stream()
                .map(this::mapToSummaryDto)
                .toList();

        return PagedResponse.of(result, content);
    }

    /**
     * The {@code billing_type_master} names that classify as {@code billingType}.
     *
     * Derived from master data through the same normalizer automatic billing uses
     * ({@link BillingConfigurationIntegrationImpl#toBillingTypeEnum}) rather than
     * from a hardcoded list, so a new spelling added to the master table is picked
     * up without a code change. Names are lower-cased and trimmed to match the
     * {@code LOWER(TRIM(...))} comparison in the query.
     */
    private Set<String> resolveBillingTypeNames(BillingType billingType) {
        return billingTypeMasterRepository.findAll().stream()
                .map(BillingTypeMaster::getBillingTypeName)
                .filter(Objects::nonNull)
                .filter(name -> BillingConfigurationIntegrationImpl.toBillingTypeEnum(name) == billingType)
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    // -----------------------------------------------------------------------
    // Private mapping helpers
    // -----------------------------------------------------------------------

    private BillingAcquisitionSummaryDto mapToSummaryDto(BillingConfiguration bc) {
        return BillingAcquisitionSummaryDto.builder()
                .billingConfigurationId(bc.getBillingConfigurationId())
                .clientName(bc.getClient().getClientName())
                .projectName(bc.getProject().getProjectName())
                // The real project_master_reference.project_code column, null when unset.
                // getActiveConfigurations() instead synthesises a legacy "PRJ-{id}" string.
                .projectCode(bc.getProject().getProjectCode())
                .billingType(bc.getBillingType().getBillingTypeName())
                .status(resolveAcquisitionState(bc.getBillingConfigurationId()).status())
                .build();
    }

    private BillingDataAcquisitionResponseDto mapToDto(BillingConfiguration bc) {

        // Legacy project code: pmsProjectId formatted as PRJ-{id}. A real
        // project_code column does exist on project_master_reference, but this
        // endpoint's existing consumers are built against the synthesised value,
        // so it is left alone; the paginated listing exposes the real column.
        String projectCode = "PRJ-" + bc.getProject().getPmsProjectId();

        // Generation mode: read from invoice_generation_type column
        // (InvoiceGenerationType enum: MANUAL / AUTOMATIC).
        String generationMode = bc.getInvoiceGenerationType() != null
                ? bc.getInvoiceGenerationType().name()
                : null;

        AcquisitionState state = resolveAcquisitionState(bc.getBillingConfigurationId());

        return BillingDataAcquisitionResponseDto.builder()
                .billingConfigurationId(bc.getBillingConfigurationId())
                .projectId(bc.getProject().getPmsProjectId())
                .projectName(bc.getProject().getProjectName())
                .projectCode(projectCode)
                .clientName(bc.getClient().getClientName())
                .billingType(bc.getBillingType().getBillingTypeName())
                .frequency(resolveFrequency(bc))
                .currency(bc.getCurrency() != null ? bc.getCurrency().getCurrencyCode() : null)
                .billingPeriodStart(state.billingPeriodStart())
                .billingPeriodEnd(state.billingPeriodEnd())
                .generationMode(generationMode)
                .status(state.status())
                .lastInvoice(state.lastInvoice())
                .snapshotId(state.snapshotId())
                .build();
    }

    /**
     * Acquisition-level state of one configuration, as both the unpaginated
     * overview and the paginated listing report it.
     */
    private record AcquisitionState(
            String status,
            String lastInvoice,
            LocalDate billingPeriodStart,
            LocalDate billingPeriodEnd,
            UUID snapshotId) {
    }

    /**
     * Resolves the acquisition-level state of {@code billingConfigurationId}.
     *
     * Shared by every Billing Data Acquisition read path so the two listings
     * cannot report different statuses for the same configuration. Costs two
     * queries per configuration, which is why the paginated listing bounds the
     * row count.
     */
    private AcquisitionState resolveAcquisitionState(UUID billingConfigurationId) {

        // Resolve the most recent BillingAcquisition execution record for this
        // configuration. Its billingPeriodStart/End are the dates the user
        // actually selected on Acquire Snapshot — never the Billing
        // Configuration's own effectiveFrom/effectiveTo validity window.
        Optional<BillingAcquisition> acquisitionOpt = billingAcquisitionRepository
                .findFirstByBillingConfiguration_BillingConfigurationIdOrderByCreatedAtDesc(billingConfigurationId);

        // A persisted snapshot whose acquisition record was never written must
        // not be reported as NOT_ACQUIRED, or the console offers to acquire it again.
        Optional<BillingSnapshot> orphanedSnapshotOpt = billingSnapshotRepository
                .findFirstByBillingConfigurationIdOrderByCreatedDateDesc(billingConfigurationId)
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

        return new AcquisitionState(status, lastInvoice, billingPeriodStart, billingPeriodEnd, snapshotId);
    }

    /** Shown as the frequency of a Milestone Plan: its billing dates come from the plan's payment entries. */
    static final String MILESTONE_PLAN_FREQUENCY = "As per Milestone Plan";

    /**
     * The configuration's billing frequency name. A Milestone Plan has no
     * recurring frequency - each installment carries its own billing date -
     * so its absence is legitimate there. Every other billing type needs one
     * to derive its periods; a configuration without it is invalid data and is
     * reported (one listing row is never allowed to break the whole console).
     */
    private String resolveFrequency(BillingConfiguration bc) {
        if (bc.getBillingFrequency() != null) {
            return bc.getBillingFrequency().getBillingFrequencyName();
        }
        String billingTypeName = bc.getBillingType() != null ? bc.getBillingType().getBillingTypeName() : null;
        if (BillingConfigurationIntegrationImpl.toBillingTypeEnum(billingTypeName) == BillingType.MILESTONE) {
            return MILESTONE_PLAN_FREQUENCY;
        }
        log.warn("[BillingDataAcquisition] Billing Configuration {} ({}) is APPROVED and ACTIVE but has no billing "
                        + "frequency; its billing periods cannot be derived until a frequency is configured.",
                bc.getBillingConfigurationId(), billingTypeName);
        return null;
    }

    /**
     * True when {@code snapshot} has no acquisition record of its own: either
     * none exists for the configuration, or the latest one belongs to a
     * different batch - another billing period, or another acquisition batch
     * of the same period - and predates the snapshot.
     */
    private boolean isNewerThanAcquisition(BillingSnapshot snapshot, Optional<BillingAcquisition> acquisitionOpt) {
        if (acquisitionOpt.isEmpty()) {
            return true;
        }
        BillingAcquisition acquisition = acquisitionOpt.get();
        boolean sameBatch = acquisition.getSnapshotId() != null
                ? acquisition.getSnapshotId().equals(snapshot.getId())
                : snapshot.getBillingPeriodStart().equals(acquisition.getBillingPeriodStart())
                        && snapshot.getBillingPeriodEnd().equals(acquisition.getBillingPeriodEnd());
        return !sameBatch
                && snapshot.getCreatedDate() != null
                && acquisition.getCreatedAt() != null
                && snapshot.getCreatedDate().isAfter(acquisition.getCreatedAt());
    }
}
