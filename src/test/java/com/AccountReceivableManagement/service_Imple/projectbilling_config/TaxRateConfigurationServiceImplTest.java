package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRateConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRegionMaster;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRegionMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxRateConfigurationServiceImplTest {

    @Mock
    private TaxConfigurationRepository taxRateConfigurationRepository;

    @Mock
    private TaxRegionMasterRepository taxRegionMasterRepository;

    @InjectMocks
    private TaxConfigurationServiceImpl taxRateConfigurationService;

    private TaxRegionMaster activeTaxRegion;

    @BeforeEach
    void setUp() {
        activeTaxRegion = TaxRegionMaster.builder()
                .taxRegionId(UUID.randomUUID())
                .taxRegionCode("IN-KA")
                .taxRegionName("Karnataka")
                .taxRegime("GST")
                .currencyCode("INR")
                .isActive(true)
                .build();
    }

    private TaxConfigurationRequestDto validGstRequest() {
        return TaxConfigurationRequestDto.builder()
                .taxRegionId(activeTaxRegion.getTaxRegionId())
                .taxType("GST")
                .cgstRate(new BigDecimal("9.0000"))
                .sgstRate(new BigDecimal("9.0000"))
                .igstRate(BigDecimal.ZERO)
                .effectiveFrom(LocalDate.of(2026, 4, 1))
                .effectiveTo(null)
                .build();
    }

    // 1. Create valid GST configuration.
    @Test
    void create_validGstConfiguration_returnsSavedConfiguration() {
        when(taxRegionMasterRepository.findById(activeTaxRegion.getTaxRegionId()))
                .thenReturn(Optional.of(activeTaxRegion));
        when(taxRateConfigurationRepository.findOverlappingConfigurations(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(taxRateConfigurationRepository.save(any(TaxRateConfiguration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TaxConfigurationResponseDto response = taxRateConfigurationService.create(validGstRequest());

        assertThat(response.getTaxRegionId()).isEqualTo(activeTaxRegion.getTaxRegionId());
        assertThat(response.getTaxRegionCode()).isEqualTo("IN-KA");
        assertThat(response.getTaxType()).isEqualTo("GST");
        assertThat(response.getCgstRate()).isEqualByComparingTo("9.0000");
        assertThat(response.getIsActive()).isTrue();
        verify(taxRateConfigurationRepository).save(any(TaxRateConfiguration.class));
    }

    // 2. Reject missing tax region.
    @Test
    void create_missingTaxRegion_throwsResourceNotFoundException() {
        UUID unknownRegionId = UUID.randomUUID();
        TaxConfigurationRequestDto request = validGstRequest();
        request.setTaxRegionId(unknownRegionId);

        when(taxRegionMasterRepository.findById(unknownRegionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taxRateConfigurationService.create(request))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessage("Tax region could not be found.");

        verify(taxRateConfigurationRepository, never()).save(any());
    }

    // 3. Reject negative CGST rate.
    @Test
    void create_negativeCgstRate_throwsValidationException() {
        when(taxRegionMasterRepository.findById(activeTaxRegion.getTaxRegionId()))
                .thenReturn(Optional.of(activeTaxRegion));

        TaxConfigurationRequestDto request = validGstRequest();
        request.setCgstRate(new BigDecimal("-1.00"));

        assertThatThrownBy(() -> taxRateConfigurationService.create(request))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessage("Tax rates cannot be negative.");

        verify(taxRateConfigurationRepository, never()).save(any());
    }

    // 4. Reject negative SGST rate.
    @Test
    void create_negativeSgstRate_throwsValidationException() {
        when(taxRegionMasterRepository.findById(activeTaxRegion.getTaxRegionId()))
                .thenReturn(Optional.of(activeTaxRegion));

        TaxConfigurationRequestDto request = validGstRequest();
        request.setSgstRate(new BigDecimal("-1.00"));

        assertThatThrownBy(() -> taxRateConfigurationService.create(request))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessage("Tax rates cannot be negative.");

        verify(taxRateConfigurationRepository, never()).save(any());
    }

    // 5. Reject invalid effective date range.
    @Test
    void create_invalidEffectiveDateRange_throwsValidationException() {
        when(taxRegionMasterRepository.findById(activeTaxRegion.getTaxRegionId()))
                .thenReturn(Optional.of(activeTaxRegion));

        TaxConfigurationRequestDto request = validGstRequest();
        request.setEffectiveFrom(LocalDate.of(2026, 4, 1));
        request.setEffectiveTo(LocalDate.of(2026, 3, 1));

        assertThatThrownBy(() -> taxRateConfigurationService.create(request))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessage("Effective end date cannot be earlier than effective start date.");

        verify(taxRateConfigurationRepository, never()).save(any());
    }

    // 6. Retrieve configuration.
    @Test
    void getById_existingConfiguration_returnsConfiguration() {
        UUID configId = UUID.randomUUID();
        TaxRateConfiguration configuration = TaxRateConfiguration.builder()
                .taxRateConfigurationId(configId)
                .taxRegion(activeTaxRegion)
                .taxType("GST")
                .cgstRate(new BigDecimal("9.0000"))
                .sgstRate(new BigDecimal("9.0000"))
                .effectiveFrom(LocalDate.of(2026, 4, 1))
                .isActive(true)
                .build();

        when(taxRateConfigurationRepository.findById(configId)).thenReturn(Optional.of(configuration));

        TaxConfigurationResponseDto response = taxRateConfigurationService.getById(configId);

        assertThat(response.getTaxRateConfigurationId()).isEqualTo(configId);
        assertThat(response.getTaxRegionCode()).isEqualTo("IN-KA");
    }

    @Test
    void getById_missingConfiguration_throwsResourceNotFoundException() {
        UUID configId = UUID.randomUUID();
        when(taxRateConfigurationRepository.findById(configId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taxRateConfigurationService.getById(configId))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessage("Tax rate configuration not found.");
    }

    // 7. Retrieve active configurations.
    @Test
    void getActive_returnsOnlyActiveConfigurations() {
        TaxRateConfiguration active = TaxRateConfiguration.builder()
                .taxRateConfigurationId(UUID.randomUUID())
                .taxRegion(activeTaxRegion)
                .taxType("GST")
                .cgstRate(new BigDecimal("9.0000"))
                .effectiveFrom(LocalDate.of(2026, 4, 1))
                .isActive(true)
                .build();

        when(taxRateConfigurationRepository.findByIsActiveTrueOrderByEffectiveFromDesc())
                .thenReturn(List.of(active));

        List<TaxConfigurationResponseDto> response = taxRateConfigurationService.getActive();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getIsActive()).isTrue();
    }

    // 8. Retrieve configuration by tax region.
    @Test
    void getByTaxRegion_returnsConfigurationsForRegion() {
        TaxRateConfiguration configuration = TaxRateConfiguration.builder()
                .taxRateConfigurationId(UUID.randomUUID())
                .taxRegion(activeTaxRegion)
                .taxType("GST")
                .cgstRate(new BigDecimal("9.0000"))
                .effectiveFrom(LocalDate.of(2026, 4, 1))
                .isActive(true)
                .build();

        when(taxRegionMasterRepository.findById(activeTaxRegion.getTaxRegionId()))
                .thenReturn(Optional.of(activeTaxRegion));
        when(taxRateConfigurationRepository
                .findByTaxRegion_TaxRegionIdAndIsActiveTrueOrderByEffectiveFromDesc(activeTaxRegion.getTaxRegionId()))
                .thenReturn(List.of(configuration));

        List<TaxConfigurationResponseDto> response =
                taxRateConfigurationService.getByTaxRegion(activeTaxRegion.getTaxRegionId());

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getTaxRegionId()).isEqualTo(activeTaxRegion.getTaxRegionId());
    }

    // 9. Deactivate configuration.
    @Test
    void deactivate_existingConfiguration_setsIsActiveFalse() {
        UUID configId = UUID.randomUUID();
        TaxRateConfiguration configuration = TaxRateConfiguration.builder()
                .taxRateConfigurationId(configId)
                .taxRegion(activeTaxRegion)
                .taxType("GST")
                .cgstRate(new BigDecimal("9.0000"))
                .effectiveFrom(LocalDate.of(2026, 4, 1))
                .isActive(true)
                .build();

        when(taxRateConfigurationRepository.findById(configId)).thenReturn(Optional.of(configuration));
        when(taxRateConfigurationRepository.save(any(TaxRateConfiguration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        taxRateConfigurationService.deactivate(configId);

        ArgumentCaptor<TaxRateConfiguration> captor = ArgumentCaptor.forClass(TaxRateConfiguration.class);
        verify(taxRateConfigurationRepository).save(captor.capture());
        assertThat(captor.getValue().getIsActive()).isFalse();
    }

    // 10. Detect conflicting/overlapping effective periods.
    @Test
    void create_overlappingEffectivePeriod_throwsValidationException() {
        when(taxRegionMasterRepository.findById(activeTaxRegion.getTaxRegionId()))
                .thenReturn(Optional.of(activeTaxRegion));

        TaxRateConfiguration existing = TaxRateConfiguration.builder()
                .taxRateConfigurationId(UUID.randomUUID())
                .taxRegion(activeTaxRegion)
                .taxType("GST")
                .cgstRate(new BigDecimal("9.0000"))
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .isActive(true)
                .build();

        when(taxRateConfigurationRepository.findOverlappingConfigurations(any(), any(), any(), any()))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> taxRateConfigurationService.create(validGstRequest()))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessage("An active tax configuration already exists for this tax region and effective period.");

        verify(taxRateConfigurationRepository, never()).save(any());
    }

    // 11. Reject inactive/missing tax region.
    @Test
    void create_inactiveTaxRegion_throwsValidationException() {
        TaxRegionMaster inactiveRegion = TaxRegionMaster.builder()
                .taxRegionId(UUID.randomUUID())
                .taxRegionCode("IN-MH")
                .taxRegionName("Maharashtra")
                .taxRegime("GST")
                .currencyCode("INR")
                .isActive(false)
                .build();

        TaxConfigurationRequestDto request = validGstRequest();
        request.setTaxRegionId(inactiveRegion.getTaxRegionId());

        when(taxRegionMasterRepository.findById(inactiveRegion.getTaxRegionId()))
                .thenReturn(Optional.of(inactiveRegion));

        assertThatThrownBy(() -> taxRateConfigurationService.create(request))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessage("Tax region is inactive and cannot be used for a tax rate configuration.");

        verify(taxRateConfigurationRepository, never()).save(any());
    }

    // 12. Verify BigDecimal precision.
    @Test
    void create_preservesBigDecimalScale() {
        when(taxRegionMasterRepository.findById(activeTaxRegion.getTaxRegionId()))
                .thenReturn(Optional.of(activeTaxRegion));
        when(taxRateConfigurationRepository.findOverlappingConfigurations(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(taxRateConfigurationRepository.save(any(TaxRateConfiguration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TaxConfigurationRequestDto request = validGstRequest();
        request.setCgstRate(new BigDecimal("9.1234"));
        request.setSgstRate(new BigDecimal("9.1234"));

        TaxConfigurationResponseDto response = taxRateConfigurationService.create(request);

        assertThat(response.getCgstRate().scale()).isEqualTo(4);
        assertThat(response.getCgstRate()).isEqualByComparingTo(new BigDecimal("9.1234"));
    }
}
