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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates Story 2.1's Billing Data Acquisition workflow. Coordinates
 * the existing Integration, Strategy, Validator, Builder, and Repository
 * components; contains no mapping, validation, acquisition, or persistence
 * logic of its own.
 */
@Service
public class BillingSnapshotServiceImpl implements BillingSnapshotService {

    private static final DateTimeFormatter SNAPSHOT_NUMBER_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BillingSnapshotRepository billingSnapshotRepository;
    private final BillingConfigurationIntegration billingConfigurationIntegration;
    private final ProjectMasterDataService projectMasterDataService;
    private final BillingAcquisitionValidator billingAcquisitionValidator;
    private final BillingSnapshotBuilder billingSnapshotBuilder;
    private final BillingSnapshotMapper billingSnapshotMapper;
    private final Map<BillingType, BillingAcquisitionStrategy> strategiesByBillingType;

    public BillingSnapshotServiceImpl(BillingSnapshotRepository billingSnapshotRepository,
                                       BillingConfigurationIntegration billingConfigurationIntegration,
                                       ProjectMasterDataService projectMasterDataService,
                                       BillingAcquisitionValidator billingAcquisitionValidator,
                                       BillingSnapshotBuilder billingSnapshotBuilder,
                                       BillingSnapshotMapper billingSnapshotMapper,
                                       List<BillingAcquisitionStrategy> strategies) {
        this.billingSnapshotRepository = billingSnapshotRepository;
        this.billingConfigurationIntegration = billingConfigurationIntegration;
        this.projectMasterDataService = projectMasterDataService;
        this.billingAcquisitionValidator = billingAcquisitionValidator;
        this.billingSnapshotBuilder = billingSnapshotBuilder;
        this.billingSnapshotMapper = billingSnapshotMapper;
        this.strategiesByBillingType = strategies.stream()
                .collect(Collectors.toMap(BillingAcquisitionStrategy::getSupportedBillingType, Function.identity()));
    }

    @Override
    public ApiResponse<BillingSnapshotResponseDto> createBillingSnapshot(BillingSnapshotCreateRequestDto request) {
        if (request.getBillingPeriodStart().isAfter(request.getBillingPeriodEnd())) {
            return ApiResponse.failure("Billing period start date cannot be after end date.");
        }

        if (billingSnapshotRepository.existsByProjectIdAndBillingPeriodStartAndBillingPeriodEnd(
                request.getProjectId(), request.getBillingPeriodStart(), request.getBillingPeriodEnd())) {
            return ApiResponse.failure(
                    "Billing Snapshot already exists for the selected project and billing period.");
        }

        BillingConfigurationResponseDto configuration = loadApprovedBillingConfiguration(request.getProjectId());
        if (configuration == null || !configuration.isApproved()) {
            return ApiResponse.failure("Approved Billing Configuration not found.");
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
        // TODO: Replace with the authenticated principal once security/auth is introduced.
        String createdBy = "SYSTEM";
        BillingSnapshotStatus status = BillingSnapshotStatus.READY_FOR_TAX;

        BillingSnapshotBuilderContext context = buildContext(
                configuration, request, validationResult.getAcquisitionResult(),
                clientId, snapshotNumber, createdBy, status, amounts);

        BillingSnapshot snapshot = billingSnapshotBuilder.build(context);
        BillingSnapshot savedSnapshot = persistSnapshot(snapshot);

        BillingSnapshotResponseDto responseDto = billingSnapshotMapper.toResponse(savedSnapshot, configuration);
        return ApiResponse.success("Billing Snapshot created successfully.", responseDto);
    }

    private BillingConfigurationResponseDto loadApprovedBillingConfiguration(Long projectId) {
        return billingConfigurationIntegration.getApprovedBillingConfiguration(projectId);
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
        return billingSnapshotRepository.save(snapshot);
    }
}
