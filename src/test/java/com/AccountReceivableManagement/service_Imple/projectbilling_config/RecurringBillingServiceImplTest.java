package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.RecurringBillingRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.RenewalRequestDto;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFrequencyMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingRecurringConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingContext;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ContractValueSource;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalOptionType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationUnit;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingFrequencyMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingRecurringConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingPeriodCalculatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.AccountReceivableManagement.service_interface.concurrency_approval.RecordActionLockService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringBillingServiceImplTest {

    @Mock private BillingRecurringConfigurationRepository billingRecurringRepository;
    @Mock private BillingConfigurationRepository billingConfigurationRepository;
    @Mock private BillingFrequencyMasterRepository billingFrequencyRepository;
    @Mock private BillingScheduleRepository billingScheduleRepository;
    @Mock private BillingPeriodCalculatorService billingPeriodCalculatorService;
    @Mock private BillingOccurrenceServiceImpl billingOccurrenceService;
    @Mock private RecordActionLockService recordActionLockService;

    private RecurringBillingServiceImpl service;

    private UUID billingConfigurationId;
    private BillingConfiguration configuration;
    private ProjectMasterReference project;
    private BillingFrequencyMaster monthlyFrequency;
    private BillingTypeMaster billingType;

    @BeforeEach
    void setUp() {
        service = new RecurringBillingServiceImpl(
                billingRecurringRepository,
                billingConfigurationRepository,
                billingFrequencyRepository,
                billingScheduleRepository,
                billingPeriodCalculatorService,
                billingOccurrenceService,
                recordActionLockService
        );

        billingConfigurationId = UUID.randomUUID();
        
        project = ProjectMasterReference.builder()
                .pmsProjectId(1L)
                .projectBudget(new BigDecimal("600000"))
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .build();

        monthlyFrequency = BillingFrequencyMaster.builder()
                .billingFrequencyId(UUID.randomUUID())
                .billingFrequencyName("MONTHLY")
                .durationValue(1)
                .durationUnit(RenewalDurationUnit.MONTHS)
                .isActive(true)
                .build();

        billingType = BillingTypeMaster.builder()
                .billingTypeId(UUID.randomUUID())
                .billingTypeName("Subscription")
                .isActive(true)
                .build();

        configuration = BillingConfiguration.builder()
                .billingConfigurationId(billingConfigurationId)
                .project(project)
                .billingFrequency(monthlyFrequency)
                .billingType(billingType)
                .approvalStatus(ApprovalStatus.DRAFT)
                .billingContext(BillingContext.PROJECT)
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .effectiveTo(LocalDate.of(2026, 12, 31))
                .build();
    }

    @Test
    void createRecurringConfiguration_WithProjectBudget_ShouldUseProjectBudget() {
        // Arrange
        RecurringBillingRequestDto request = RecurringBillingRequestDto.builder()
                .contractValueSource(ContractValueSource.PMS_BUDGET)
                .contractValue(null) // Should be ignored
                .billingFrequencyId(monthlyFrequency.getBillingFrequencyId())
                .recurringStartDate(LocalDate.of(2026, 1, 1))
                .recurringEndDate(LocalDate.of(2026, 12, 31))
                .build();

        when(billingConfigurationRepository.findById(billingConfigurationId))
                .thenReturn(Optional.of(configuration));
        when(billingFrequencyRepository.findById(monthlyFrequency.getBillingFrequencyId()))
                .thenReturn(Optional.of(monthlyFrequency));
        when(billingRecurringRepository.existsByBillingConfigurationAndIsActiveTrue(any()))
                .thenReturn(false);
        when(billingRecurringRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(billingConfigurationRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        var response = service.create(billingConfigurationId, request);

        // Assert
        assertThat(response.getContractValue()).isEqualTo(new BigDecimal("600000"));
        assertThat(response.getContractValueSource()).isEqualTo(ContractValueSource.PMS_BUDGET);
        verify(billingConfigurationRepository).save(argThat(conf -> 
                conf.getContractValue().equals(new BigDecimal("600000"))
        ));
    }

    @Test
    void createRecurringConfiguration_WithProjectBudget_NoProject_ShouldThrowException() {
        // Arrange
        configuration.setProject(null);
        RecurringBillingRequestDto request = RecurringBillingRequestDto.builder()
                .contractValueSource(ContractValueSource.PMS_BUDGET)
                .billingFrequencyId(monthlyFrequency.getBillingFrequencyId())
                .recurringStartDate(LocalDate.of(2026, 1, 1))
                .recurringEndDate(LocalDate.of(2026, 12, 31))
                .build();

        when(billingConfigurationRepository.findById(billingConfigurationId))
                .thenReturn(Optional.of(configuration));
        when(billingFrequencyRepository.findById(monthlyFrequency.getBillingFrequencyId()))
                .thenReturn(Optional.of(monthlyFrequency));
        when(billingRecurringRepository.existsByBillingConfigurationAndIsActiveTrue(any()))
                .thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> service.create(billingConfigurationId, request))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Project is required when using Project Budget");
    }

    @Test
    void createRecurringConfiguration_WithProjectBudget_NoProjectBudget_ShouldThrowException() {
        // Arrange
        project.setProjectBudget(null);
        RecurringBillingRequestDto request = RecurringBillingRequestDto.builder()
                .contractValueSource(ContractValueSource.PMS_BUDGET)
                .billingFrequencyId(monthlyFrequency.getBillingFrequencyId())
                .recurringStartDate(LocalDate.of(2026, 1, 1))
                .recurringEndDate(LocalDate.of(2026, 12, 31))
                .build();

        when(billingConfigurationRepository.findById(billingConfigurationId))
                .thenReturn(Optional.of(configuration));
        when(billingFrequencyRepository.findById(monthlyFrequency.getBillingFrequencyId()))
                .thenReturn(Optional.of(monthlyFrequency));
        when(billingRecurringRepository.existsByBillingConfigurationAndIsActiveTrue(any()))
                .thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> service.create(billingConfigurationId, request))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Project Budget is not available");
    }

    @Test
    void createRecurringConfiguration_WithProjectBudget_ZeroBudget_ShouldThrowException() {
        // Arrange
        project.setProjectBudget(BigDecimal.ZERO);
        RecurringBillingRequestDto request = RecurringBillingRequestDto.builder()
                .contractValueSource(ContractValueSource.PMS_BUDGET)
                .billingFrequencyId(monthlyFrequency.getBillingFrequencyId())
                .recurringStartDate(LocalDate.of(2026, 1, 1))
                .recurringEndDate(LocalDate.of(2026, 12, 31))
                .build();

        when(billingConfigurationRepository.findById(billingConfigurationId))
                .thenReturn(Optional.of(configuration));
        when(billingFrequencyRepository.findById(monthlyFrequency.getBillingFrequencyId()))
                .thenReturn(Optional.of(monthlyFrequency));
        when(billingRecurringRepository.existsByBillingConfigurationAndIsActiveTrue(any()))
                .thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> service.create(billingConfigurationId, request))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Project Budget must be greater than zero");
    }

    @Test
    void createRecurringConfiguration_WithManualBudget_ShouldUseManualBudget() {
        // Arrange
        RecurringBillingRequestDto request = RecurringBillingRequestDto.builder()
                .contractValueSource(ContractValueSource.MANUAL)
                .contractValue(new BigDecimal("300000"))
                .billingFrequencyId(monthlyFrequency.getBillingFrequencyId())
                .recurringStartDate(LocalDate.of(2026, 1, 1))
                .recurringEndDate(LocalDate.of(2026, 6, 30))
                .build();

        when(billingConfigurationRepository.findById(billingConfigurationId))
                .thenReturn(Optional.of(configuration));
        when(billingFrequencyRepository.findById(monthlyFrequency.getBillingFrequencyId()))
                .thenReturn(Optional.of(monthlyFrequency));
        when(billingRecurringRepository.existsByBillingConfigurationAndIsActiveTrue(any()))
                .thenReturn(false);
        when(billingRecurringRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(billingConfigurationRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        var response = service.create(billingConfigurationId, request);

        // Assert
        assertThat(response.getContractValue()).isEqualTo(new BigDecimal("300000"));
        assertThat(response.getContractValueSource()).isEqualTo(ContractValueSource.MANUAL);
    }

    @Test
    void createRecurringConfiguration_WithManualBudget_NoAmount_ShouldThrowException() {
        // Arrange
        RecurringBillingRequestDto request = RecurringBillingRequestDto.builder()
                .contractValueSource(ContractValueSource.MANUAL)
                .contractValue(null)
                .billingFrequencyId(monthlyFrequency.getBillingFrequencyId())
                .recurringStartDate(LocalDate.of(2026, 1, 1))
                .recurringEndDate(LocalDate.of(2026, 12, 31))
                .build();

        when(billingConfigurationRepository.findById(billingConfigurationId))
                .thenReturn(Optional.of(configuration));
        when(billingFrequencyRepository.findById(monthlyFrequency.getBillingFrequencyId()))
                .thenReturn(Optional.of(monthlyFrequency));
        when(billingRecurringRepository.existsByBillingConfigurationAndIsActiveTrue(any()))
                .thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> service.create(billingConfigurationId, request))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Manual Budget is required");
    }

    @Test
    void createRecurringConfiguration_WithManualBudget_ZeroAmount_ShouldThrowException() {
        // Arrange
        RecurringBillingRequestDto request = RecurringBillingRequestDto.builder()
                .contractValueSource(ContractValueSource.MANUAL)
                .contractValue(BigDecimal.ZERO)
                .billingFrequencyId(monthlyFrequency.getBillingFrequencyId())
                .recurringStartDate(LocalDate.of(2026, 1, 1))
                .recurringEndDate(LocalDate.of(2026, 12, 31))
                .build();

        when(billingConfigurationRepository.findById(billingConfigurationId))
                .thenReturn(Optional.of(configuration));
        when(billingFrequencyRepository.findById(monthlyFrequency.getBillingFrequencyId()))
                .thenReturn(Optional.of(monthlyFrequency));
        when(billingRecurringRepository.existsByBillingConfigurationAndIsActiveTrue(any()))
                .thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> service.create(billingConfigurationId, request))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Manual Budget must be greater than zero");
    }

    @Test
    void createRecurringConfiguration_EffectiveToBeforeEffectiveFrom_ShouldThrowException() {
        // Arrange
        RecurringBillingRequestDto request = RecurringBillingRequestDto.builder()
                .contractValueSource(ContractValueSource.MANUAL)
                .contractValue(new BigDecimal("300000"))
                .billingFrequencyId(monthlyFrequency.getBillingFrequencyId())
                .recurringStartDate(LocalDate.of(2026, 12, 31))
                .recurringEndDate(LocalDate.of(2026, 1, 1))
                .build();

        when(billingConfigurationRepository.findById(billingConfigurationId))
                .thenReturn(Optional.of(configuration));
        when(billingFrequencyRepository.findById(monthlyFrequency.getBillingFrequencyId()))
                .thenReturn(Optional.of(monthlyFrequency));
        when(billingRecurringRepository.existsByBillingConfigurationAndIsActiveTrue(any()))
                .thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> service.create(billingConfigurationId, request))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("cannot be after");
    }

    @Test
    void renewRecurringConfiguration_ShouldNotRequireRecurringPurpose() {
        // Arrange
        UUID recurringConfigId = UUID.randomUUID();
        BillingRecurringConfiguration originalRecurring = BillingRecurringConfiguration.builder()
                .recurringConfigurationId(recurringConfigId)
                .recurringName("Test Subscription")
                .contractValue(new BigDecimal("300000"))
                .contractValueSource(ContractValueSource.MANUAL)
                .billingFrequency(monthlyFrequency)
                .recurringStartDate(LocalDate.of(2026, 1, 1))
                .recurringEndDate(LocalDate.of(2026, 6, 30))
                .billingConfiguration(configuration)
                .isActive(true)
                .build();

        configuration.setBillingContext(BillingContext.PRODUCT_SERVICE);
        configuration.setApprovalStatus(ApprovalStatus.APPROVED);

        RenewalRequestDto renewalRequest = RenewalRequestDto.builder()
                .renewalOptionType(RenewalOptionType.SAME_AS_PREVIOUS)
                .effectiveFrom(LocalDate.of(2026, 7, 1))
                .effectiveTo(LocalDate.of(2026, 12, 31))
                .build();

        when(billingRecurringRepository.findById(recurringConfigId))
                .thenReturn(Optional.of(originalRecurring));
        when(billingRecurringRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(billingConfigurationRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        var response = service.renew(recurringConfigId, renewalRequest);

        // Assert
        assertThat(response.getContractValue()).isEqualTo(new BigDecimal("300000"));
        assertThat(response.getContractValueSource()).isEqualTo(ContractValueSource.MANUAL);
        // Verify that recurringPurpose is not set (it should be null in the response)
    }
}
