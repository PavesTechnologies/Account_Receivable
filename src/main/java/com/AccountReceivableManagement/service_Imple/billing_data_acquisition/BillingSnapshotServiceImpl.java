package com.AccountReceivableManagement.service_Imple.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.AcquireDataResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.AcquisitionScope;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionResultDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotCreateRequestDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.TimesheetDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.ValidationResultDto;
import com.AccountReceivableManagement.dto.common.ApiResponse;
import com.AccountReceivableManagement.builder.billing_data_acquisition.BillingSnapshotBuilder;
import com.AccountReceivableManagement.builder.billing_data_acquisition.BillingSnapshotBuilderContext;
import com.AccountReceivableManagement.dependency.billing_data_acquisition.ProjectMasterDataService;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshotItem;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingAcquisitionStatus;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;
import com.AccountReceivableManagement.integration.billing_data_acquisition.BillingConfigurationIntegration;
import com.AccountReceivableManagement.mapper.billing_data_acquisition.BillingSnapshotMapper;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingAcquisitionService;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingSnapshotService;
import com.AccountReceivableManagement.strategy.billing_data_acquisition.BillingAcquisitionStrategy;
import com.AccountReceivableManagement.validator.billing_data_acquisition.BillingAcquisitionValidator;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates Story 2.1's Billing Data Acquisition workflow. Coordinates
 * the existing Integration, Strategy, Validator, Builder, and Repository
 * components; contains no mapping, validation, acquisition, or persistence
 * logic of its own.
 */
@Slf4j
@Service
public class BillingSnapshotServiceImpl implements BillingSnapshotService {

    private static final DateTimeFormatter SNAPSHOT_NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int SNAPSHOT_NUMBER_SUFFIX_LENGTH = 8;
    private static final String BILLED_SOURCE_CONSTRAINT = "uk_billing_snapshot_item_billed_source";
    private static final String PERIOD_BATCH_CONSTRAINT = "uk_billing_snapshot_period_batch";
    private static final String IDEMPOTENCY_CONSTRAINT = "uk_billing_snapshot_config_idempotency";
    /**
     * Period-level keys removed by the DBA script that follows migration V19: while either still exists,
     * a period cannot take a second batch.
     */
    private static final String LEGACY_SNAPSHOT_PERIOD_CONSTRAINT = "uk_billing_snapshot_project_period";
    private static final String LEGACY_ACQUISITION_PERIOD_CONSTRAINT = "uk_billing_acq_config_period";

    private final BillingSnapshotRepository billingSnapshotRepository;
    private final BillingConfigurationIntegration billingConfigurationIntegration;
    private final ProjectMasterDataService projectMasterDataService;
    private final BillingAcquisitionValidator billingAcquisitionValidator;
    private final BillingSnapshotBuilder billingSnapshotBuilder;
    private final BillingSnapshotMapper billingSnapshotMapper;
    private final com.AccountReceivableManagement.repo.projectbilling_config.CurrencyMasterRepository currencyMasterRepository;
    private final com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository projectMasterReferenceRepository;
    private final com.AccountReceivableManagement.repo.projectbilling_config.TaxRegionMasterRepository taxRegionMasterRepository;
    private final Map<BillingType, BillingAcquisitionStrategy> strategiesByBillingType;
    private final BillingAcquisitionService billingAcquisitionService;
    private final com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository invoiceRepository;
    private final TransactionTemplate transactionTemplate;

    /** Resolves a T&amp;M configuration's billing periods (see {@link #timeAndMaterialPeriodError}). */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.AccountReceivableManagement.service_Imple.projectbilling_config.BillingOccurrenceServiceImpl
            billingOccurrenceService;

    @org.springframework.beans.factory.annotation.Autowired
    public BillingSnapshotServiceImpl(BillingSnapshotRepository billingSnapshotRepository,
            BillingConfigurationIntegration billingConfigurationIntegration,
            ProjectMasterDataService projectMasterDataService,
            BillingAcquisitionValidator billingAcquisitionValidator,
            BillingSnapshotBuilder billingSnapshotBuilder,
            BillingSnapshotMapper billingSnapshotMapper,
            com.AccountReceivableManagement.repo.projectbilling_config.CurrencyMasterRepository currencyMasterRepository,
            com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository projectMasterReferenceRepository,
            com.AccountReceivableManagement.repo.projectbilling_config.TaxRegionMasterRepository taxRegionMasterRepository,
            List<BillingAcquisitionStrategy> strategies,
            BillingAcquisitionService billingAcquisitionService,
            PlatformTransactionManager transactionManager,
            com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository invoiceRepository) {
        this.billingSnapshotRepository = billingSnapshotRepository;
        this.billingConfigurationIntegration = billingConfigurationIntegration;
        this.projectMasterDataService = projectMasterDataService;
        this.billingAcquisitionValidator = billingAcquisitionValidator;
        this.billingSnapshotBuilder = billingSnapshotBuilder;
        this.billingSnapshotMapper = billingSnapshotMapper;
        this.currencyMasterRepository = currencyMasterRepository;
        this.projectMasterReferenceRepository = projectMasterReferenceRepository;
        this.taxRegionMasterRepository = taxRegionMasterRepository;
        this.strategiesByBillingType = strategies.stream()
                .collect(Collectors.toMap(BillingAcquisitionStrategy::getSupportedBillingType, Function.identity()));
        this.billingAcquisitionService = billingAcquisitionService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.invoiceRepository = invoiceRepository;
    }

    public BillingSnapshotServiceImpl(BillingSnapshotRepository billingSnapshotRepository,
            BillingConfigurationIntegration billingConfigurationIntegration,
            ProjectMasterDataService projectMasterDataService,
            BillingAcquisitionValidator billingAcquisitionValidator,
            BillingSnapshotBuilder billingSnapshotBuilder,
            BillingSnapshotMapper billingSnapshotMapper,
            com.AccountReceivableManagement.repo.projectbilling_config.CurrencyMasterRepository currencyMasterRepository,
            com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository projectMasterReferenceRepository,
            com.AccountReceivableManagement.repo.projectbilling_config.TaxRegionMasterRepository taxRegionMasterRepository,
            List<BillingAcquisitionStrategy> strategies,
            BillingAcquisitionService billingAcquisitionService,
            PlatformTransactionManager transactionManager) {
        this(billingSnapshotRepository, billingConfigurationIntegration, projectMasterDataService,
                billingAcquisitionValidator, billingSnapshotBuilder, billingSnapshotMapper,
                currencyMasterRepository, projectMasterReferenceRepository, taxRegionMasterRepository,
                strategies, billingAcquisitionService, transactionManager, null);
    }

    /**
     * Original single-snapshot acquisition: when the project's billing period already has a snapshot, that
     * snapshot is returned as stored (its first live batch, see {@link #primarySnapshot}); otherwise batch 1
     * is acquired, and any already-claimed source record fails the acquisition. Automatic billing relies on
     * this reuse to resume a run without calling TMS again. Newly approved records of an already-acquired
     * period are acquired with {@link #createIncrementalBatch}.
     */
    @Override
    public ApiResponse<BillingSnapshotResponseDto> createBillingSnapshot(BillingSnapshotCreateRequestDto request) {
        if (request.getBillingPeriodStart().isAfter(request.getBillingPeriodEnd())) {
            return ApiResponse.failure("Billing period start date cannot be after end date.");
        }

        List<BillingSnapshot> existing = findExistingSnapshots(request);
        if (!existing.isEmpty()) {
            return reuseOrRejectExistingSnapshot(existing, request);
        }

        String periodError = timeAndMaterialPeriodError(request);
        if (periodError != null) {
            return ApiResponse.failure(periodError);
        }

        return acquireAndPersist(request, AcquisitionScope.FAIL_ON_CLAIMED, null);
    }

    /**
     * A new Time &amp; Material snapshot must cover exactly one of the configuration's billing periods (frequency
     * and Effective From), as incremental batches already must - an arbitrary range (e.g. a calendar month on
     * a weekly schedule) would not match any period. Only new snapshots are checked: an existing snapshot is
     * reused or rejected above, unchanged. {@code null} when the period is acceptable or not T&amp;M.
     */
    private String timeAndMaterialPeriodError(BillingSnapshotCreateRequestDto request) {
        if (billingOccurrenceService == null) {
            return null; // instance built without Spring (unit tests); production always injects it
        }
        BillingConfigurationResponseDto configuration = loadApprovedBillingConfiguration(request);
        if (configuration == null || !configuration.isApproved()
                || configuration.getBillingType() != BillingType.TIME_AND_MATERIAL
                || configuration.getBillingConfigurationId() == null) {
            return null; // acquireAndPersist reports a missing/unapproved configuration itself
        }
        boolean configuredPeriod = billingOccurrenceService
                .resolveTimeAndMaterialBillingPeriods(configuration.getBillingConfigurationId(),
                        request.getBillingPeriodEnd())
                .stream()
                .anyMatch(period -> period.getPeriodStartDate().equals(request.getBillingPeriodStart())
                        && period.getPeriodEndDate().equals(request.getBillingPeriodEnd()));
        return configuredPeriod ? null
                : "Billing period " + request.getBillingPeriodStart() + " to " + request.getBillingPeriodEnd()
                        + " is not one of this Time & Material configuration's billing periods ("
                        + configuration.getBillingFrequencyName() + "). Use a period returned by period discovery.";
    }

    /** Longest idempotency key the billing_snapshot.idempotency_key column holds. */
    static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    /**
     * Incremental Time &amp; Material acquisition - see the interface Javadoc. Never reuses, changes or
     * re-prices an existing snapshot of the period: it acquires only source records no live snapshot claims
     * yet, into a new batch (snapshot + acquisition record, committed together), or creates nothing.
     */
    @Override
    public ApiResponse<BillingSnapshotResponseDto> createIncrementalBatch(BillingSnapshotCreateRequestDto request,
            String idempotencyKey) {
        if (request.getBillingPeriodStart().isAfter(request.getBillingPeriodEnd())) {
            return ApiResponse.failure("Billing period start date cannot be after end date.");
        }
        if (request.getBillingConfigurationId() == null) {
            return ApiResponse.failure("Billing Configuration is required for incremental acquisition.");
        }
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (key != null && key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            return ApiResponse.failure("Idempotency key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH
                    + " characters.");
        }
        if (key != null) {
            Optional<BillingSnapshot> replay = billingSnapshotRepository
                    .findByBillingConfigurationIdAndIdempotencyKey(request.getBillingConfigurationId(), key);
            if (replay.isPresent()) {
                return replayBatch(replay.get(), request, key);
            }
        }
        return acquireAndPersist(request, AcquisitionScope.newBatch(), key);
    }

    /**
     * Acquires, validates and persists one new snapshot (acquisition batch) for the request's period.
     * {@code scope} decides how already-claimed source records are treated; a non-{@link
     * AcquisitionScope#FAIL_ON_CLAIMED} scope is the incremental path.
     */
    private ApiResponse<BillingSnapshotResponseDto> acquireAndPersist(BillingSnapshotCreateRequestDto request,
            AcquisitionScope scope, String idempotencyKey) {
        boolean incremental = !AcquisitionScope.FAIL_ON_CLAIMED.equals(scope);

        BillingConfigurationResponseDto configuration = loadApprovedBillingConfiguration(request);
        if (configuration == null || !configuration.isApproved()) {
            return ApiResponse.failure("Approved Billing Configuration not found.");
        }

        // Resolve source tax jurisdiction from BillingConfiguration
        String sourceTaxJurisdictionCode = resolveSourceTaxJurisdiction(configuration);

        // Resolve destination tax jurisdiction from ProjectMasterReference.primaryLocation
        String destinationTaxJurisdictionCode = resolveDestinationTaxJurisdiction(request.getProjectId());

        // G-13: Validate currency from configuration without silent INR or arbitrary first-row fallback
        UUID currencyId = configuration.getCurrencyId();
        String currencyCode = configuration.getCurrencyCode();
        if (currencyId == null && (currencyCode == null || currencyCode.isBlank())) {
            return ApiResponse.failure("Currency configuration is missing for billing configuration "
                    + configuration.getBillingConfigurationId() + ".");
        }

        Optional<com.AccountReceivableManagement.entity.projectbilling_config.CurrencyMaster> currencyOpt = Optional.empty();
        if (currencyId != null) {
            currencyOpt = currencyMasterRepository.findById(currencyId);
        }
        if (currencyOpt.isEmpty() && currencyCode != null && !currencyCode.isBlank()) {
            currencyOpt = currencyMasterRepository.findByCurrencyCodeIgnoreCase(currencyCode.trim());
        }

        if (currencyOpt.isEmpty()) {
            return ApiResponse.failure("Configured currency (" + (currencyCode != null ? currencyCode : currencyId)
                    + ") is not a supported active currency.");
        }

        configuration.setCurrencyId(currencyOpt.get().getCurrencyId());
        configuration.setCurrencyCode(currencyOpt.get().getCurrencyCode());

        BillingAcquisitionStrategy strategy = resolveStrategy(configuration.getBillingType());
        if (strategy == null) {
            return ApiResponse.failure("Billing type " + configuration.getBillingType() + " is not yet supported.");
        }

        UUID clientId = projectMasterDataService.getClientIdByProjectId(request.getProjectId());

        log.info("[BillingSnapshotStage] stage=acquire projectId={} billingConfigurationId={} period={}..{} billingType={}",
                request.getProjectId(), request.getBillingConfigurationId(),
                request.getBillingPeriodStart(), request.getBillingPeriodEnd(), configuration.getBillingType());
        BillingAcquisitionResultDto acquisitionResult = incremental
                ? strategy.acquire(configuration, request, scope)
                : strategy.acquire(configuration, request);
        log.info("[BillingSnapshotStage] stage=acquired projectId={} timesheets={} expenses={} alreadyClaimed={}",
                request.getProjectId(),
                acquisitionResult != null && acquisitionResult.getTimesheets() != null
                        ? acquisitionResult.getTimesheets().size() : 0,
                acquisitionResult != null && acquisitionResult.getExpenses() != null
                        ? acquisitionResult.getExpenses().size() : 0,
                acquisitionResult != null ? acquisitionResult.getAlreadyClaimedTimesheetCount() : 0);

        if (incremental && hasNoSourceRecords(acquisitionResult)) {
            // Never an empty snapshot: nothing new means no batch at all.
            int alreadyClaimed = acquisitionResult != null ? acquisitionResult.getAlreadyClaimedTimesheetCount() : 0;
            return ApiResponse.failure("No new approved, billable timesheets or eligible expenses were found for "
                    + "billing period " + request.getBillingPeriodStart() + " to " + request.getBillingPeriodEnd()
                    + (alreadyClaimed > 0 ? " (" + alreadyClaimed + " approved timesheet(s) are already billed in "
                    + "earlier batches)" : "") + "; no billing batch was created.");
        }

        ValidationResultDto validationResult = billingAcquisitionValidator.validate(acquisitionResult);
        if (!validationResult.isSuccess()) {
            log.warn("[BillingSnapshotStage] stage=validation-failed projectId={} reason={}",
                    request.getProjectId(), validationResult.getValidationMessage());
            return ApiResponse.failure(validationResult.getValidationMessage());
        }

        BillingAmountSummary amounts = calculateAmounts(validationResult.getAcquisitionResult().getTimesheets(),
                validationResult.getAcquisitionResult().getExpenses());
        String snapshotNumber = generateSnapshotNumber();
        String createdBy = "SYSTEM";
        BillingSnapshotStatus status = BillingSnapshotStatus.READY_FOR_TAX;

        BillingSnapshotBuilderContext context = buildContext(
                configuration, request, validationResult.getAcquisitionResult(),
                clientId, snapshotNumber, createdBy, status, amounts,
                sourceTaxJurisdictionCode, destinationTaxJurisdictionCode);

        BillingSnapshot snapshot = billingSnapshotBuilder.build(context);
        snapshot.setIdempotencyKey(idempotencyKey);

        try {
            // Snapshot and its acquisition record commit together or not at all,
            // so a persisted snapshot can no longer be left without its record.
            SnapshotCreation creation = transactionTemplate.execute(txStatus -> {
                // Next batch number of the project's period; a concurrent batch taking the same
                // number fails on uk_billing_snapshot_period_batch and nothing of this one is kept.
                snapshot.setBatchSequence(nextBatchSequence(request));
                BillingSnapshot savedSnapshot = persistSnapshot(snapshot);
                AcquireDataResponseDto acquisition = billingAcquisitionService.createManualAcquisition(
                        savedSnapshot.getBillingConfigurationId(),
                        savedSnapshot.getBillingPeriodStart(),
                        savedSnapshot.getBillingPeriodEnd(),
                        savedSnapshot.getId(),
                        BillingAcquisitionStatus.READY.name());
                return new SnapshotCreation(savedSnapshot, acquisition);
            });
            BillingSnapshotResponseDto responseDto = billingSnapshotMapper.toResponse(creation.snapshot(), configuration);
            responseDto.setExistingSnapshot(false);
            applyAcquisitionStatus(responseDto, creation.acquisition());
            if (incremental) {
                responseDto.setExcludedAlreadyBilledTimesheetCount(
                        validationResult.getAcquisitionResult().getAlreadyClaimedTimesheetCount());
                return ApiResponse.success("Billing batch " + creation.snapshot().getBatchSequence()
                        + " created successfully.", responseDto);
            }
            return ApiResponse.success("Billing Snapshot created successfully.", responseDto);
        } catch (DataIntegrityViolationException ex) {
            if (incremental) {
                return resolveConcurrentBatchConflict(request, idempotencyKey, ex);
            }
            // A concurrent request for the same project and period committed first
            // (uk_billing_snapshot_project_period). This transaction has been rolled
            // back; re-read in a fresh one and reuse the winner's snapshot.
            List<BillingSnapshot> concurrentSnapshots = findExistingSnapshots(request);
            if (!concurrentSnapshots.isEmpty()) {
                log.warn("[BillingSnapshotConcurrentCreate] Snapshot for projectId={}, period {} to {} was created "
                                + "by a concurrent request; reusing it.",
                        request.getProjectId(), request.getBillingPeriodStart(), request.getBillingPeriodEnd());
                return reuseOrRejectExistingSnapshot(concurrentSnapshots, request);
            }
            if (isBilledSourceConflict(ex)) {
                // A concurrent snapshot - another period, possibly overlapping this
                // one - committed a claim on one of these timesheets first
                // (uk_billing_snapshot_item_billed_source). Nothing was saved here.
                log.warn("[BillingSnapshotSourceAlreadyClaimed] projectId={}, period {} to {}: {}",
                        request.getProjectId(), request.getBillingPeriodStart(), request.getBillingPeriodEnd(),
                        ex.getMostSpecificCause().getMessage());
                return ApiResponse.failure("One or more timesheets or expenses of this billing period were "
                        + "included in another Billing Snapshot at the same time and cannot be billed again.");
            }
            log.error("[BillingSnapshotPersistenceError] Snapshot creation failed for projectId={}: {}",
                    request.getProjectId(), ex.getMostSpecificCause().getMessage(), ex);
            return ApiResponse.failure("Failed to save Billing Snapshot: " + ex.getMostSpecificCause().getMessage());
        } catch (GlobalExceptionHandler.ValidationException | GlobalExceptionHandler.ResourceNotFoundException ex) {
            // Acquisition-record validation (e.g. configuration not active) - the
            // snapshot was rolled back with it; surface the real message, as the
            // other validation failures in this method do.
            throw ex;
        } catch (org.springframework.dao.TransientDataAccessException | org.springframework.transaction.TransactionException ex) {
            // G-16: Transient database connectivity/lock failures must propagate as retryable exceptions
            // rather than being converted to permanent ApiResponse.failure
            log.warn("[BillingSnapshotTransientPersistenceError] Transient failure during snapshot creation for projectId={}: {}",
                    request.getProjectId(), ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("[BillingSnapshotPersistenceError] Snapshot creation failed for projectId={}: {}",
                    request.getProjectId(), ex.getMessage(), ex);
            return ApiResponse.failure("Failed to save Billing Snapshot: " + ex.getMessage());
        }
    }

    @Override
    public ApiResponse<BillingSnapshotResponseDto> getByProjectAndPeriod(Long projectId,
            LocalDate billingPeriodStart, LocalDate billingPeriodEnd) {
        return getByProjectAndPeriod(projectId, billingPeriodStart, billingPeriodEnd, null);
    }

    @Override
    public ApiResponse<BillingSnapshotResponseDto> getByProjectAndPeriod(Long projectId,
            LocalDate billingPeriodStart, LocalDate billingPeriodEnd, UUID billingConfigurationId) {
        List<BillingSnapshot> snapshots = billingSnapshotRepository
                .findAllByProjectIdAndBillingPeriodStartAndBillingPeriodEndOrderByCreatedDateAsc(projectId,
                        billingPeriodStart, billingPeriodEnd);

        if (snapshots == null || snapshots.isEmpty()) {
            return ApiResponse.failure(
                    "Billing Snapshot not found for the selected project and billing period.");
        }

        // The (project, period) is shared by every configuration the project has
        // had, so a snapshot of another configuration is not this configuration's.
        List<BillingSnapshot> candidates = billingConfigurationId == null ? snapshots : snapshots.stream()
                .filter(candidate -> billingConfigurationId.equals(candidate.getBillingConfigurationId()))
                .toList();
        if (candidates.isEmpty()) {
            BillingSnapshot other = snapshots.get(0);
            log.warn("[BillingSnapshotConfigurationMismatch] snapshotId={} belongs to billingConfigurationId={}, "
                            + "requested billingConfigurationId={}; not returned.",
                    other.getId(), other.getBillingConfigurationId(), billingConfigurationId);
            return ApiResponse.failure(
                    "Billing Snapshot not found for the selected billing configuration and billing period.");
        }

        // A period acquired in several batches: its first live batch, as createBillingSnapshot reuses.
        // Every batch is listed by GET /billing-snapshots?billingConfigurationId=.
        BillingSnapshot snapshot = primarySnapshot(candidates);

        BillingConfigurationResponseDto configuration =
                resolveHistoricalConfiguration(snapshot.getBillingConfigurationId());

        BillingSnapshotResponseDto responseDto = billingSnapshotMapper.toResponse(snapshot, configuration);
        return ApiResponse.success("Billing Snapshot retrieved successfully.", responseDto);
    }

    @Override
    public ApiResponse<List<BillingSnapshotResponseDto>> getByBillingConfiguration(UUID billingConfigurationId) {
        List<BillingSnapshot> snapshots = billingSnapshotRepository
                .findAllByBillingConfigurationIdOrderByBillingPeriodStartDesc(billingConfigurationId);

        if (snapshots.isEmpty()) {
            return ApiResponse.success("No Billing Snapshots found for the selected billing configuration.",
                    List.of());
        }

        // All of them share the one configuration, so its names are resolved once.
        BillingConfigurationResponseDto configuration = resolveHistoricalConfiguration(billingConfigurationId);

        return ApiResponse.success("Billing Snapshots retrieved successfully.", snapshots.stream()
                .map(snapshot -> billingSnapshotMapper.toResponse(snapshot, configuration))
                .toList());
    }

    /**
     * Phase 2B financial correction - see the interface Javadoc. Re-fetches
     * authoritative source data for the snapshot's own, already-known
     * {@code billingConfigurationId} and billing period, validates it with
     * the same {@link BillingAcquisitionValidator} used at first-time
     * creation, and only then mutates the existing, already-persisted
     * {@code BillingSnapshot} - never a new one. Validation failure leaves
     * the snapshot completely untouched (no field is set before validation
     * succeeds).
     */
    @Override
    @Transactional
    public BillingSnapshot rebuildBillingSnapshot(UUID billingSnapshotId) {
        return rebuildBillingSnapshotWithAudit(billingSnapshotId).snapshot();
    }

    @Override
    @Transactional
    public com.AccountReceivableManagement.dto.billing_data_acquisition.SnapshotRebuildResult rebuildBillingSnapshotWithAudit(
            UUID billingSnapshotId) {

        BillingSnapshot snapshot = billingSnapshotRepository.findById(billingSnapshotId)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                        "Billing snapshot could not be found."));

        BillingConfigurationResponseDto configuration =
                billingConfigurationIntegration.getApprovedBillingConfigurationById(
                        snapshot.getBillingConfigurationId());

        if (configuration == null || !configuration.isApproved()) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Approved Billing Configuration not found.");
        }

        BillingAcquisitionStrategy strategy = resolveStrategy(configuration.getBillingType());
        if (strategy == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing type " + configuration.getBillingType() + " is not yet supported.");
        }

        // Re-derived from the snapshot's own, already-frozen identity - never a
        // project+period search, per the correction workflow's exact-snapshot contract.
        BillingSnapshotCreateRequestDto request = BillingSnapshotCreateRequestDto.builder()
                .projectId(snapshot.getProjectId())
                .billingConfigurationId(snapshot.getBillingConfigurationId())
                .billingPeriodStart(snapshot.getBillingPeriodStart())
                .billingPeriodEnd(snapshot.getBillingPeriodEnd())
                .build();

        // The real TMS call happens here, for Time & Material - reusing the
        // existing strategy as first-time acquisition does. The period may hold
        // other batches: their records are left out (never moved into this one);
        // this snapshot's own records and any still-unclaimed ones are re-acquired.
        BillingAcquisitionResultDto acquisitionResult =
                strategy.acquire(configuration, request, AcquisitionScope.rebuildOf(snapshot.getId()));

        // The snapshot's own items are excluded - they are what is being replaced.
        ValidationResultDto validationResult =
                billingAcquisitionValidator.validate(acquisitionResult, snapshot.getId());
        if (!validationResult.isSuccess()) {
            // Nothing on the snapshot has been touched yet - fail closed.
            throw new GlobalExceptionHandler.ValidationException(validationResult.getValidationMessage());
        }

        List<TimesheetDto> timesheets = validationResult.getAcquisitionResult().getTimesheets();
        keepFrozenConversions(snapshot, validationResult.getAcquisitionResult().getExpenses());
        BillingAmountSummary amounts = calculateAmounts(timesheets,
                validationResult.getAcquisitionResult().getExpenses());

        List<BillingSnapshotItem> rebuiltItems = billingSnapshotBuilder.buildItems(timesheets,
                validationResult.getAcquisitionResult().getExpenses());
        rebuiltItems.forEach(item -> item.setBillingSnapshot(snapshot));

        // The rebuild re-prices from the CURRENT approved rate schedule (that is the purpose of the correction, and
        // a schedule edit already forces re-approval, which the isApproved() guard above enforces). The replaced
        // lines are not kept, so every difference from the original pricing is written to the log first.
        int[] counts = new int[3];
        describeRepricing(snapshot.getItems(), rebuiltItems, counts)
                .forEach(change -> log.warn("[SnapshotRebuildRepriced] snapshotId={} {}", snapshot.getId(), change));
        String repricingSummary = summarizeRepricing(counts, snapshot.getSubtotal(), amounts.getSubtotal(),
                snapshot.getTotalAmount(), amounts.getTotalAmount());

        // Replace items in place - the managed collection's orphanRemoval mapping
        // deletes what's cleared and inserts what's added, same pattern already
        // used by InvoiceServiceImpl.refreshInvoiceFromAuthoritativeData().
        snapshot.getItems().clear();
        // Hibernate inserts before it deletes orphans; the old items must be gone
        // before the rebuilt ones re-claim the same timesheets
        // (uk_billing_snapshot_item_billed_source).
        billingSnapshotRepository.flush();
        snapshot.getItems().addAll(rebuiltItems);

        snapshot.setSubtotal(amounts.getSubtotal());
        snapshot.setExpenseAmount(amounts.getExpenseAmount());
        snapshot.setTotalAmount(amounts.getTotalAmount());

        // Only for the correction workflow - lets the existing, unmodified
        // TaxCalculationService.calculateTax(UUID) run again for this snapshot.
        snapshot.setStatus(BillingSnapshotStatus.READY_FOR_TAX);
        snapshot.setUpdatedBy("SYSTEM");
        snapshot.setUpdatedDate(LocalDateTime.now());

        return new com.AccountReceivableManagement.dto.billing_data_acquisition.SnapshotRebuildResult(
                billingSnapshotRepository.save(snapshot), repricingSummary);
    }

    /** Upper bound for the summary; the invoice approval-history comment column is VARCHAR(500). */
    static final int MAX_SUMMARY_LENGTH = 300;

    /**
     * Concise audit text for the approval history: line counts plus previous and revised subtotal/total. The
     * counts-only form is used when the full form would exceed {@link #MAX_SUMMARY_LENGTH}, so the text is
     * always complete, never cut mid-way.
     *
     * @param counts {added, removed, repriced} as filled by {@link #describeRepricing(List, List, int[])}
     */
    static String summarizeRepricing(int[] counts, BigDecimal oldSubtotal, BigDecimal newSubtotal,
                                     BigDecimal oldTotal, BigDecimal newTotal) {
        String lines = counts[2] + " repriced, " + counts[0] + " added, " + counts[1] + " removed time-entry line(s)";
        String full = "Rebuilt from current rate schedule: " + lines + "; subtotal " + money(oldSubtotal) + " -> "
                + money(newSubtotal) + ", total " + money(oldTotal) + " -> " + money(newTotal) + ".";
        return full.length() <= MAX_SUMMARY_LENGTH ? full : "Rebuilt from current rate schedule: " + lines + ".";
    }

    private static String money(BigDecimal value) {
        return value == null ? "n/a" : value.toPlainString();
    }

    /**
     * Human-readable differences between the timesheet lines a snapshot had and the ones a rebuild produced,
     * matched on the TMS source reference: rate, base rate, discount period, inclusion and amount changes, plus
     * lines added or removed. Empty when the rebuild priced everything exactly as before.
     */
    static List<String> describeRepricing(List<BillingSnapshotItem> before, List<BillingSnapshotItem> after) {
        return describeRepricing(before, after, new int[3]);
    }

    /** As above; {@code counts} receives {added, removed, repriced}. */
    static List<String> describeRepricing(List<BillingSnapshotItem> before, List<BillingSnapshotItem> after,
                                          int[] counts) {
        java.util.Map<String, BillingSnapshotItem> previous = new java.util.LinkedHashMap<>();
        for (BillingSnapshotItem item : before) {
            if (item.getItemType() == com.AccountReceivableManagement.entity_enums.billing_data_acquisition
                    .BillingItemType.TIME_ENTRY) {
                previous.put(item.getSourceReferenceId(), item);
            }
        }
        List<String> changes = new java.util.ArrayList<>();
        for (BillingSnapshotItem item : after) {
            if (item.getItemType() != com.AccountReceivableManagement.entity_enums.billing_data_acquisition
                    .BillingItemType.TIME_ENTRY) {
                continue;
            }
            BillingSnapshotItem old = previous.remove(item.getSourceReferenceId());
            if (old == null) {
                counts[0]++;
                changes.add("line " + item.getSourceReferenceId() + " added, rate " + item.getRate()
                        + ", amount " + item.getAmount());
                continue;
            }
            List<String> diffs = new java.util.ArrayList<>();
            addDiff(diffs, "rate", old.getRate(), item.getRate());
            addDiff(diffs, "baseRate", old.getBaseRate(), item.getBaseRate());
            addDiff(diffs, "discountAmount", old.getDiscountAmount(), item.getDiscountAmount());
            addDiff(diffs, "amount", old.getAmount(), item.getAmount());
            if (!java.util.Objects.equals(old.getDiscountPeriodId(), item.getDiscountPeriodId())) {
                diffs.add("discountPeriod " + old.getDiscountPeriodName() + " -> " + item.getDiscountPeriodName());
            }
            boolean wasIncluded = com.AccountReceivableManagement.util.TmRateCalculator
                    .isIncluded(old.getIncludeInInvoice());
            boolean isIncluded = com.AccountReceivableManagement.util.TmRateCalculator
                    .isIncluded(item.getIncludeInInvoice());
            if (wasIncluded != isIncluded) {
                diffs.add("includeInInvoice " + wasIncluded + " -> " + isIncluded);
            }
            if (!diffs.isEmpty()) {
                counts[2]++;
                changes.add("line " + item.getSourceReferenceId() + " repriced: " + String.join(", ", diffs));
            }
        }
        counts[1] += previous.size();
        previous.keySet().forEach(ref -> changes.add("line " + ref + " removed"));
        return changes;
    }

    private static void addDiff(List<String> diffs, String field, BigDecimal before, BigDecimal after) {
        boolean same = before == null ? after == null : after != null && before.compareTo(after) == 0;
        if (!same) {
            diffs.add(field + " " + before + " -> " + after);
        }
    }

    /**
     * A rebuild re-acquires expenses, which re-converts any cross-currency line. An expense the snapshot
     * already converted keeps its frozen rate and billed amount - a rebuild must never change an existing
     * conversion. It applies only while XMS still reports the same source amount and currency; anything
     * else (or a new expense) keeps the freshly acquired conversion.
     */
    private void keepFrozenConversions(BillingSnapshot snapshot,
            List<com.AccountReceivableManagement.dto.billing_data_acquisition.ExpenseLineDto> expenses) {
        if (expenses == null) {
            return;
        }
        for (com.AccountReceivableManagement.dto.billing_data_acquisition.ExpenseLineDto expense : expenses) {
            if (expense.getExchangeRate() == null) {
                continue;
            }
            snapshot.getItems().stream()
                    .filter(item -> item.getItemType() == com.AccountReceivableManagement.entity_enums
                            .billing_data_acquisition.BillingItemType.EXPENSE)
                    .filter(item -> expense.getLineItemId().equals(item.getSourceReferenceId()))
                    .filter(item -> item.getExchangeRate() != null && item.getSourceAmount() != null
                            && item.getSourceAmount().compareTo(expense.getSourceAmount()) == 0
                            && item.getSourceCurrency() != null
                            && item.getSourceCurrency().equalsIgnoreCase(expense.getSourceCurrency()))
                    .findFirst()
                    .ifPresent(item -> {
                        expense.setCostBasis(item.getAmount());
                        expense.setExchangeRate(item.getExchangeRate());
                        expense.setExchangeRateDate(item.getExchangeRateDate());
                        expense.setExchangeRateSource(item.getExchangeRateSource());
                    });
        }
    }

    /** Every snapshot (batch) of the request's project and period, oldest first. */
    private List<BillingSnapshot> findExistingSnapshots(BillingSnapshotCreateRequestDto request) {
        List<BillingSnapshot> snapshots = billingSnapshotRepository
                .findAllByProjectIdAndBillingPeriodStartAndBillingPeriodEndOrderByCreatedDateAsc(
                        request.getProjectId(), request.getBillingPeriodStart(), request.getBillingPeriodEnd());
        return snapshots != null ? snapshots : List.of();
    }

    /**
     * The snapshot that stands for a period acquired in one or more batches: the first batch that is not
     * CANCELLED, or - every batch cancelled - the latest. {@code snapshots} is oldest first and not empty.
     */
    static BillingSnapshot primarySnapshot(List<BillingSnapshot> snapshots) {
        return snapshots.stream()
                .sorted(java.util.Comparator
                        .comparing((BillingSnapshot s) -> s.getBatchSequence() != null ? s.getBatchSequence() : 1)
                        .thenComparing(BillingSnapshot::getCreatedDate,
                                java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .filter(s -> s.getStatus() != BillingSnapshotStatus.CANCELLED)
                .findFirst()
                .orElse(snapshots.get(snapshots.size() - 1));
    }

    private Integer nextBatchSequence(BillingSnapshotCreateRequestDto request) {
        Integer highest = billingSnapshotRepository.findMaxBatchSequence(request.getProjectId(),
                request.getBillingPeriodStart(), request.getBillingPeriodEnd());
        return highest == null ? 1 : highest + 1;
    }

    private static boolean hasNoSourceRecords(BillingAcquisitionResultDto result) {
        return result == null
                || ((result.getTimesheets() == null || result.getTimesheets().isEmpty())
                && (result.getExpenses() == null || result.getExpenses().isEmpty()));
    }

    /**
     * A repeated incremental request (same configuration and idempotency key) returns the batch the first
     * request created, unchanged - never a second batch. The key is bound to one billing period.
     */
    private ApiResponse<BillingSnapshotResponseDto> replayBatch(BillingSnapshot existing,
            BillingSnapshotCreateRequestDto request, String idempotencyKey) {
        if (!existing.getBillingPeriodStart().equals(request.getBillingPeriodStart())
                || !existing.getBillingPeriodEnd().equals(request.getBillingPeriodEnd())) {
            return ApiResponse.failure("Idempotency key '" + idempotencyKey + "' was already used for billing "
                    + "period " + existing.getBillingPeriodStart() + " to " + existing.getBillingPeriodEnd()
                    + " (Billing Snapshot " + existing.getSnapshotNumber() + "); use a new key for another period.");
        }
        log.info("[BillingBatchReplay] billingConfigurationId={} idempotencyKey={} -> snapshotId={} batch={}",
                existing.getBillingConfigurationId(), idempotencyKey, existing.getId(), existing.getBatchSequence());
        ApiResponse<BillingSnapshotResponseDto> reused = reuseExistingSnapshot(existing);
        return ApiResponse.success("Billing batch " + existing.getBatchSequence()
                + " was already created by this request; returned unchanged.", reused.getData());
    }

    /**
     * An incremental batch lost a race - nothing of it was saved (one transaction). The same request
     * (idempotency key) committed first: its batch is returned. Otherwise the caller is told why; a retry
     * acquires only what is still unclaimed, so it can never bill a record twice.
     */
    private ApiResponse<BillingSnapshotResponseDto> resolveConcurrentBatchConflict(
            BillingSnapshotCreateRequestDto request, String idempotencyKey, DataIntegrityViolationException ex) {
        if (idempotencyKey != null) {
            Optional<BillingSnapshot> winner = billingSnapshotRepository
                    .findByBillingConfigurationIdAndIdempotencyKey(request.getBillingConfigurationId(),
                            idempotencyKey);
            if (winner.isPresent()) {
                return replayBatch(winner.get(), request, idempotencyKey);
            }
        }
        String cause = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        String lower = cause != null ? cause.toLowerCase() : "";
        log.warn("[BillingBatchConflict] projectId={} period {} to {}: {}", request.getProjectId(),
                request.getBillingPeriodStart(), request.getBillingPeriodEnd(), cause);
        if (lower.contains(LEGACY_SNAPSHOT_PERIOD_CONSTRAINT) || lower.contains(LEGACY_ACQUISITION_PERIOD_CONSTRAINT)) {
            return ApiResponse.failure("This billing period already has a Billing Snapshot and the database does not "
                    + "yet allow additional billing batches per period (the DBA script "
                    + "scripts/dba/V19_incremental_tm_billing_batches_dba.sql that follows migration V19 has not been "
                    + "applied). Nothing was created.");
        }
        if (lower.contains(BILLED_SOURCE_CONSTRAINT)) {
            return ApiResponse.failure("One or more of these timesheets or expenses were included in another billing "
                    + "batch at the same time; nothing was created. Refresh Data Acquisition and retry - only "
                    + "records that are still unbilled will be acquired.");
        }
        if (lower.contains(PERIOD_BATCH_CONSTRAINT) || lower.contains(IDEMPOTENCY_CONSTRAINT)) {
            return ApiResponse.failure("Another billing batch for this period was created at the same time; nothing "
                    + "was created. Refresh Data Acquisition and retry.");
        }
        log.error("[BillingSnapshotPersistenceError] Batch creation failed for projectId={}: {}",
                request.getProjectId(), cause, ex);
        return ApiResponse.failure("Failed to save Billing Snapshot: " + cause);
    }

    /**
     * Snapshots are unique per (project, period), not per configuration, so the
     * row found for a request may belong to a different - e.g. earlier,
     * since-replaced - Billing Configuration of the same project. Handing that
     * snapshot back would show the requested configuration another
     * configuration's lifecycle state (including INVOICED), so it is rejected
     * instead, naming the conflicting snapshot. A request that names no
     * configuration is checked against the project's currently approved one,
     * when that can be resolved.
     */
    private ApiResponse<BillingSnapshotResponseDto> reuseOrRejectExistingSnapshot(List<BillingSnapshot> snapshots,
            BillingSnapshotCreateRequestDto request) {
        UUID requestedConfigurationId = resolveRequestedConfigurationId(request);
        List<BillingSnapshot> ownBatches = requestedConfigurationId == null ? snapshots : snapshots.stream()
                .filter(candidate -> requestedConfigurationId.equals(candidate.getBillingConfigurationId()))
                .toList();
        if (!ownBatches.isEmpty()) {
            return reuseExistingSnapshot(primarySnapshot(ownBatches));
        }
        BillingSnapshot existing = snapshots.get(0);
        log.warn("[BillingSnapshotConfigurationMismatch] projectId={}, period {} to {}: snapshotId={} ({}, {}) "
                        + "belongs to billingConfigurationId={}, requested billingConfigurationId={}; not reused.",
                request.getProjectId(), request.getBillingPeriodStart(), request.getBillingPeriodEnd(),
                existing.getId(), existing.getSnapshotNumber(), existing.getStatus(),
                existing.getBillingConfigurationId(), requestedConfigurationId);
        return ApiResponse.failure("Billing Snapshot " + existing.getSnapshotNumber() + " (status "
                + existing.getStatus() + ") already exists for this project and billing period under a "
                + "different Billing Configuration (" + existing.getBillingConfigurationId() + "). It cannot "
                + "be reused for the selected Billing Configuration; choose another billing period or "
                + "resolve the earlier snapshot.");
    }

    private UUID resolveRequestedConfigurationId(BillingSnapshotCreateRequestDto request) {
        if (request.getBillingConfigurationId() != null) {
            return request.getBillingConfigurationId();
        }
        try {
            BillingConfigurationResponseDto active =
                    billingConfigurationIntegration.getApprovedBillingConfiguration(request.getProjectId());
            return active != null ? active.getBillingConfigurationId() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Returns an already-persisted snapshot exactly as stored - same id,
     * snapshot number, totals and lifecycle status, never rebuilt - after
     * making sure its acquisition record exists and references it.
     */
    private ApiResponse<BillingSnapshotResponseDto> reuseExistingSnapshot(BillingSnapshot existing) {
        BillingConfigurationResponseDto configuration =
                resolveHistoricalConfiguration(existing.getBillingConfigurationId());
        BillingSnapshotResponseDto responseDto = billingSnapshotMapper.toResponse(existing, configuration);
        responseDto.setExistingSnapshot(true);
        applyAcquisitionStatus(responseDto, reconcileAcquisition(existing));
        return ApiResponse.success(
                "Billing Snapshot already exists for the selected project and billing period.", responseDto);
    }

    /**
     * Repairs a missing or stale acquisition record for an existing snapshot.
     * A configuration that can no longer be recorded against (deleted,
     * unapproved or inactive) must not hide the snapshot itself, so those
     * validation failures are logged and the snapshot is still returned.
     */
    private AcquireDataResponseDto reconcileAcquisition(BillingSnapshot existing) {
        try {
            return billingAcquisitionService.recordAcquisitionForSnapshot(
                    existing.getBillingConfigurationId(),
                    existing.getBillingPeriodStart(),
                    existing.getBillingPeriodEnd(),
                    existing.getId());
        } catch (GlobalExceptionHandler.ValidationException | GlobalExceptionHandler.ResourceNotFoundException ex) {
            log.warn("[BillingAcquisitionReconcileSkipped] snapshotId={}, billingConfigurationId={}: {}",
                    existing.getId(), existing.getBillingConfigurationId(), ex.getMessage());
            return null;
        }
    }

    private static boolean isBilledSourceConflict(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase().contains(BILLED_SOURCE_CONSTRAINT);
    }

    private void applyAcquisitionStatus(BillingSnapshotResponseDto responseDto, AcquireDataResponseDto acquisition) {
        if (acquisition != null && acquisition.getStatus() != null) {
            responseDto.setAcquisitionStatus(acquisition.getStatus());
        }
    }

    private record SnapshotCreation(BillingSnapshot snapshot, AcquireDataResponseDto acquisition) {
    }

    /**
     * Resolves the Billing Configuration referenced by a snapshot's frozen,
     * historical {@code billingConfigurationId}. That id is never re-pointed
     * at the project's currently active configuration; if the historical
     * configuration has since been deleted, the snapshot itself must still
     * be returned, so only the display-enrichment fields are affected.
     */
    private BillingConfigurationResponseDto resolveHistoricalConfiguration(UUID billingConfigurationId) {
        try {
            return billingConfigurationIntegration.getApprovedBillingConfigurationById(billingConfigurationId);
        } catch (GlobalExceptionHandler.ResourceNotFoundException ex) {
            log.warn(
                    "[BillingSnapshotHistoricalConfigurationMissing] billingConfigurationId={} could not be resolved; "
                            + "returning snapshot with configuration-derived fields omitted. Reason: {}",
                    billingConfigurationId, ex.getMessage());
            return BillingConfigurationResponseDto.builder().build();
        }
    }

    private BillingConfigurationResponseDto loadApprovedBillingConfiguration(BillingSnapshotCreateRequestDto request) {
        if (request.getBillingConfigurationId() != null) {
            try {
                BillingConfigurationResponseDto config = billingConfigurationIntegration
                        .getApprovedBillingConfigurationById(request.getBillingConfigurationId());
                if (config != null) {
                    return config;
                }
            } catch (Exception ex) {
                log.warn("Lookup failed for billingConfigurationId={}, falling back to projectId lookup",
                        request.getBillingConfigurationId());
            }
        }
        return billingConfigurationIntegration.getApprovedBillingConfiguration(request.getProjectId());
    }

    private BillingAcquisitionStrategy resolveStrategy(BillingType billingType) {
        return strategiesByBillingType.get(billingType);
    }

    static BillingAmountSummary calculateAmounts(List<TimesheetDto> timesheets,
            List<com.AccountReceivableManagement.dto.billing_data_acquisition.ExpenseLineDto> expenses) {
        // Lines excluded from the invoice stay on the snapshot for audit but never reach the payable
        // subtotal, and therefore never the tax base (tax is calculated from the snapshot total).
        // Sum of the rounded line amounts - exactly what the snapshot items carry.
        BigDecimal subtotal = timesheets.stream()
                .filter(timesheet -> com.AccountReceivableManagement.util.TmRateCalculator
                        .isIncluded(timesheet.getIncludeInInvoice()))
                .map(timesheet -> com.AccountReceivableManagement.util.TmRateCalculator
                        .lineAmount(timesheet.getHours(), timesheet.getHourlyRate()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // costBasis is already the billable base-currency amount: summed as-is.
        BigDecimal expenseAmount = expenses == null ? BigDecimal.ZERO : expenses.stream()
                .map(com.AccountReceivableManagement.dto.billing_data_acquisition.ExpenseLineDto::getCostBasis)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return BillingAmountSummary.builder()
                .subtotal(subtotal)
                .expenseAmount(expenseAmount)
                .totalAmount(subtotal.add(expenseAmount))
                .build();
    }

    /**
     * {@code BS-yyyyMMddHHmmss-XXXXXXXX} (26 chars, fits the 30-char
     * snapshot_number columns). The random hex suffix keeps numbers unique
     * when several snapshots are created within the same second; earlier
     * {@code BS-yyyyMMddHHmmss} numbers are left as they are.
     */
    String generateSnapshotNumber() {
        String suffix = UUID.randomUUID().toString().replace("-", "")
                .substring(0, SNAPSHOT_NUMBER_SUFFIX_LENGTH).toUpperCase();
        return "BS-" + LocalDateTime.now().format(SNAPSHOT_NUMBER_FORMATTER) + "-" + suffix;
    }

    private BillingSnapshotBuilderContext buildContext(BillingConfigurationResponseDto configuration,
            BillingSnapshotCreateRequestDto request,
            BillingAcquisitionResultDto acquisitionResult,
            UUID clientId,
            String snapshotNumber,
            String createdBy,
            BillingSnapshotStatus status,
            BillingAmountSummary amounts,
            String sourceTaxJurisdictionCode,
            String destinationTaxJurisdictionCode) {
        return BillingSnapshotBuilderContext.builder()
                .configuration(configuration)
                .request(request)
                .acquisitionResult(acquisitionResult)
                .clientId(clientId)
                .snapshotNumber(snapshotNumber)
                .createdBy(createdBy)
                .status(status)
                .subtotal(amounts.getSubtotal())
                .expenseAmount(amounts.getExpenseAmount())
                .totalAmount(amounts.getTotalAmount())
                .sourceTaxJurisdictionCode(sourceTaxJurisdictionCode)
                .destinationTaxJurisdictionCode(destinationTaxJurisdictionCode)
                .build();
    }

    private String resolveSourceTaxJurisdiction(BillingConfigurationResponseDto configuration) {
        if (configuration.getTaxRegionCode() == null || configuration.getTaxRegionCode().isBlank()) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Tax jurisdiction could not be determined because the billing configuration has no tax region.");
        }
        return configuration.getTaxRegionCode().trim().toUpperCase();
    }

    private String resolveDestinationTaxJurisdiction(Long projectId) {
        com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference project =
                projectMasterReferenceRepository.findBypmsProjectId(projectId)
                        .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                                "Project not found with id: " + projectId));

        String primaryLocation = project.getPrimaryLocation();
        if (primaryLocation == null || primaryLocation.isBlank()) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Client tax jurisdiction could not be determined because project primary location is missing.");
        }

        String locationName = primaryLocation.trim();
        return taxRegionMasterRepository.findByTaxRegionNameIgnoreCase(locationName)
                .map(taxRegion -> {
                    String code = taxRegion.getTaxRegionCode();
                    if (code == null || code.isBlank()) {
                        throw new GlobalExceptionHandler.ValidationException(
                                "Tax region code is missing for client location: " + locationName);
                    }
                    return code.trim().toUpperCase();
                })
                .orElseThrow(() -> new GlobalExceptionHandler.ValidationException(
                        "No tax region found for client location: " + locationName));
    }

    private BillingSnapshot persistSnapshot(BillingSnapshot snapshot) {
        // Identifiers only - amounts and client/project details stay out of routine logs (G-31).
        log.info("[BillingSnapshotPreSave] snapshotNumber={}, billingConfigurationId={}, billingPeriod={}..{}, status={}",
                snapshot.getSnapshotNumber(),
                snapshot.getBillingConfigurationId(),
                snapshot.getBillingPeriodStart(),
                snapshot.getBillingPeriodEnd(),
                snapshot.getStatus());

        try {
            // Flushed so a unique-key conflict surfaces here, not at commit.
            return billingSnapshotRepository.saveAndFlush(snapshot);
        } catch (DataIntegrityViolationException | org.springframework.dao.TransientDataAccessException | org.springframework.transaction.TransactionException ex) {
            // Left untranslated - caller and retry policy distinguish races and transient failures.
            throw ex;
        } catch (Exception ex) {
            Throwable rootCause = ex;
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }
            log.error("[BillingSnapshotSaveFailure] Persistence failed for snapshotNumber={}. Root Cause: {}",
                    snapshot.getSnapshotNumber(), rootCause.getMessage(), ex);
            throw new IllegalStateException("Database insert error [" + rootCause.getMessage() + "]", ex);
        }
    }

    @Override
    @Transactional
    public ApiResponse<Void> cancelBillingSnapshot(UUID billingSnapshotId) {
        if (billingSnapshotId == null) {
            return ApiResponse.failure("Snapshot ID is required.");
        }
        BillingSnapshot snapshot = billingSnapshotRepository.findById(billingSnapshotId)
                .orElse(null);
        if (snapshot == null) {
            return ApiResponse.failure("Billing Snapshot not found: " + billingSnapshotId);
        }
        if (snapshot.getStatus() == BillingSnapshotStatus.CANCELLED) {
            return ApiResponse.success("Billing Snapshot is already cancelled.", null);
        }

        if (invoiceRepository != null && invoiceRepository.existsByBillingSnapshotId(billingSnapshotId)) {
            throw new IllegalStateException("Cannot cancel Billing Snapshot " + billingSnapshotId
                    + " as an invoice has already been generated from it.");
        }

        snapshot.setStatus(BillingSnapshotStatus.CANCELLED);
        if (snapshot.getItems() != null) {
            for (BillingSnapshotItem item : snapshot.getItems()) {
                item.setBilledSourceReferenceId(null);
            }
        }
        billingSnapshotRepository.save(snapshot);
        log.info("[BillingSnapshotCancelled] snapshotId={} snapshotNumber={} claims released.",
                billingSnapshotId, snapshot.getSnapshotNumber());
        return ApiResponse.success("Billing Snapshot cancelled and source claims released successfully.", null);
    }
}
