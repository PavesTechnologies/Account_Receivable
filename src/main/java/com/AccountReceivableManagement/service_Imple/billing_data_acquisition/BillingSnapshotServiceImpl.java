package com.AccountReceivableManagement.service_Imple.billing_data_acquisition;

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
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;
import com.AccountReceivableManagement.integration.billing_data_acquisition.BillingConfigurationIntegration;
import com.AccountReceivableManagement.mapper.billing_data_acquisition.BillingSnapshotMapper;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingSnapshotService;
import com.AccountReceivableManagement.strategy.billing_data_acquisition.BillingAcquisitionStrategy;
import com.AccountReceivableManagement.validator.billing_data_acquisition.BillingAcquisitionValidator;
import org.springframework.stereotype.Service;

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

    private final BillingSnapshotRepository billingSnapshotRepository;
    private final BillingConfigurationIntegration billingConfigurationIntegration;
    private final ProjectMasterDataService projectMasterDataService;
    private final BillingAcquisitionValidator billingAcquisitionValidator;
    private final BillingSnapshotBuilder billingSnapshotBuilder;
    private final BillingSnapshotMapper billingSnapshotMapper;
    private final com.AccountReceivableManagement.repo.projectbilling_config.CurrencyMasterRepository currencyMasterRepository;
    private final Map<BillingType, BillingAcquisitionStrategy> strategiesByBillingType;

    public BillingSnapshotServiceImpl(BillingSnapshotRepository billingSnapshotRepository,
            BillingConfigurationIntegration billingConfigurationIntegration,
            ProjectMasterDataService projectMasterDataService,
            BillingAcquisitionValidator billingAcquisitionValidator,
            BillingSnapshotBuilder billingSnapshotBuilder,
            BillingSnapshotMapper billingSnapshotMapper,
            com.AccountReceivableManagement.repo.projectbilling_config.CurrencyMasterRepository currencyMasterRepository,
            List<BillingAcquisitionStrategy> strategies) {
        this.billingSnapshotRepository = billingSnapshotRepository;
        this.billingConfigurationIntegration = billingConfigurationIntegration;
        this.projectMasterDataService = projectMasterDataService;
        this.billingAcquisitionValidator = billingAcquisitionValidator;
        this.billingSnapshotBuilder = billingSnapshotBuilder;
        this.billingSnapshotMapper = billingSnapshotMapper;
        this.currencyMasterRepository = currencyMasterRepository;
        this.strategiesByBillingType = strategies.stream()
                .collect(Collectors.toMap(BillingAcquisitionStrategy::getSupportedBillingType, Function.identity()));
    }

    @Override
    public ApiResponse<BillingSnapshotResponseDto> createBillingSnapshot(BillingSnapshotCreateRequestDto request) {
        if (request.getBillingPeriodStart().isAfter(request.getBillingPeriodEnd())) {
            return ApiResponse.failure("Billing period start date cannot be after end date.");
        }

        Optional<BillingSnapshot> existingOpt = billingSnapshotRepository.findByProjectIdAndBillingPeriodStartAndBillingPeriodEnd(
                request.getProjectId(), request.getBillingPeriodStart(), request.getBillingPeriodEnd());
        if (existingOpt.isPresent()) {
            BillingSnapshot existing = existingOpt.get();
            BillingConfigurationResponseDto configuration = billingConfigurationIntegration
                    .getApprovedBillingConfigurationById(existing.getBillingConfigurationId());
            BillingSnapshotResponseDto responseDto = billingSnapshotMapper.toResponse(existing, configuration);
            return ApiResponse.success(
                    "Billing Snapshot already exists for the selected project and billing period.", responseDto);
        }

        BillingConfigurationResponseDto configuration = loadApprovedBillingConfiguration(request);
        if (configuration == null || !configuration.isApproved()) {
            return ApiResponse.failure("Approved Billing Configuration not found.");
        }

        // Fallback resolution for currencyId if not set in configuration DTO
        if (configuration.getCurrencyId() == null) {
            String code = configuration.getCurrencyCode() != null ? configuration.getCurrencyCode() : "INR";
            currencyMasterRepository.findByCurrencyCodeIgnoreCase(code)
                    .ifPresent(c -> configuration.setCurrencyId(c.getCurrencyId()));

            if (configuration.getCurrencyId() == null) {
                currencyMasterRepository.findAll().stream().findFirst()
                        .ifPresent(c -> configuration.setCurrencyId(c.getCurrencyId()));
            }
        }

        BillingAcquisitionStrategy strategy = resolveStrategy(configuration.getBillingType());
        if (strategy == null) {
            return ApiResponse.failure("Billing type " + configuration.getBillingType() + " is not yet supported.");
        }

        UUID clientId = projectMasterDataService.getClientIdByProjectId(request.getProjectId());

        BillingAcquisitionResultDto acquisitionResult = strategy.acquire(configuration, request);

        ValidationResultDto validationResult = billingAcquisitionValidator.validate(acquisitionResult);
        if (!validationResult.isSuccess()) {
            return ApiResponse.failure(validationResult.getValidationMessage());
        }

        BillingAmountSummary amounts = calculateAmounts(validationResult.getAcquisitionResult().getTimesheets());
        String snapshotNumber = generateSnapshotNumber();
        String createdBy = "SYSTEM";
        BillingSnapshotStatus status = BillingSnapshotStatus.READY_TO_TAX;

        BillingSnapshotBuilderContext context = buildContext(
                configuration, request, validationResult.getAcquisitionResult(),
                clientId, snapshotNumber, createdBy, status, amounts);

        BillingSnapshot snapshot = billingSnapshotBuilder.build(context);

        try {
            BillingSnapshot savedSnapshot = persistSnapshot(snapshot);
            BillingSnapshotResponseDto responseDto = billingSnapshotMapper.toResponse(savedSnapshot, configuration);
            return ApiResponse.success("Billing Snapshot created successfully.", responseDto);
        } catch (Exception ex) {
            log.error("[BillingSnapshotPersistenceError] Snapshot creation failed for projectId={}: {}",
                    request.getProjectId(), ex.getMessage(), ex);
            return ApiResponse.failure("Failed to save Billing Snapshot: " + ex.getMessage());
        }
    }

    @Override
    public ApiResponse<BillingSnapshotResponseDto> getByProjectAndPeriod(Long projectId,
            LocalDate billingPeriodStart, LocalDate billingPeriodEnd) {
        Optional<BillingSnapshot> snapshotOptional = billingSnapshotRepository
                .findByProjectIdAndBillingPeriodStartAndBillingPeriodEnd(projectId, billingPeriodStart,
                        billingPeriodEnd);

        if (snapshotOptional.isEmpty()) {
            return ApiResponse.failure(
                    "Billing Snapshot not found for the selected project and billing period.");
        }

        BillingSnapshot snapshot = snapshotOptional.get();
        BillingConfigurationResponseDto configuration = billingConfigurationIntegration
                .getApprovedBillingConfigurationById(snapshot.getBillingConfigurationId());

        BillingSnapshotResponseDto responseDto = billingSnapshotMapper.toResponse(snapshot, configuration);
        return ApiResponse.success("Billing Snapshot retrieved successfully.", responseDto);
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

    private BillingAmountSummary calculateAmounts(List<TimesheetDto> timesheets) {
        BigDecimal subtotal = timesheets.stream()
                .map(timesheet -> timesheet.getHours().multiply(timesheet.getHourlyRate()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expenseAmount = BigDecimal.ZERO;

        return BillingAmountSummary.builder()
                .subtotal(subtotal)
                .expenseAmount(expenseAmount)
                .totalAmount(subtotal.add(expenseAmount))
                .build();
    }

    private String generateSnapshotNumber() {
        return "BS-" + LocalDateTime.now().format(SNAPSHOT_NUMBER_FORMATTER);
    }

    private BillingSnapshotBuilderContext buildContext(BillingConfigurationResponseDto configuration,
            BillingSnapshotCreateRequestDto request,
            BillingAcquisitionResultDto acquisitionResult,
            UUID clientId,
            String snapshotNumber,
            String createdBy,
            BillingSnapshotStatus status,
            BillingAmountSummary amounts) {
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
                .build();
    }

    private BillingSnapshot persistSnapshot(BillingSnapshot snapshot) {
        log.info(
                "[BillingSnapshotPreSave] snapshotNumber={}, billingConfigurationId={}, clientId={}, projectId={}, billingTypeId={}, billingType={}, currencyId={}, currencyCode={}, paymentTermId={}, paymentTermCode={}, billingFrequencyId={}, billingFrequency={}, taxRegionId={}, taxRegionCode={}, billingPeriodStart={}, billingPeriodEnd={}, status={}, subtotal={}, expenseAmount={}, totalAmount={}",
                snapshot.getSnapshotNumber(),
                snapshot.getBillingConfigurationId(),
                snapshot.getClientId(),
                snapshot.getProjectId(),
                snapshot.getBillingTypeId(),
                snapshot.getBillingType(),
                snapshot.getCurrencyId(),
                snapshot.getCurrencyCode(),
                snapshot.getPaymentTermId(),
                snapshot.getPaymentTermCode(),
                snapshot.getBillingFrequencyId(),
                snapshot.getBillingFrequency(),
                snapshot.getTaxRegionId(),
                snapshot.getTaxRegionCode(),
                snapshot.getBillingPeriodStart(),
                snapshot.getBillingPeriodEnd(),
                snapshot.getStatus(),
                snapshot.getSubtotal(),
                snapshot.getExpenseAmount(),
                snapshot.getTotalAmount());

        try {
            return billingSnapshotRepository.save(snapshot);
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
}
