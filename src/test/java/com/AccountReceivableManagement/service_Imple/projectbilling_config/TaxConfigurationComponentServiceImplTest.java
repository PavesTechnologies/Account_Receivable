package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationComponentRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationComponentResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxConfigurationComponent;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxTypeMaster;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxApplicabilityType;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxConfigurationComponentRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxTypeMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxConfigurationComponentServiceImplTest {

    @Mock
    private TaxConfigurationComponentRepository componentRepository;

    @Mock
    private TaxConfigurationRepository taxConfigurationRepository;

    @Mock
    private TaxTypeMasterRepository taxTypeMasterRepository;

    @InjectMocks
    private TaxConfigurationComponentServiceImpl componentService;

    private UUID configId;
    private UUID taxTypeId;
    private UUID componentId;
    private TaxConfiguration configuration;
    private TaxTypeMaster taxType;
    private TaxConfigurationComponent component;
    private TaxConfigurationComponentRequestDto requestDto;

    @BeforeEach
    void setUp() {
        configId = UUID.randomUUID();
        taxTypeId = UUID.randomUUID();
        componentId = UUID.randomUUID();

        configuration = TaxConfiguration.builder()
                .taxConfigurationId(configId)
                .isActive(true)
                .build();

        taxType = TaxTypeMaster.builder()
                .taxTypeId(taxTypeId)
                .taxTypeCode("CGST")
                .taxTypeName("Central GST")
                .isActive(true)
                .build();

        component = TaxConfigurationComponent.builder()
                .taxConfigurationComponentId(componentId)
                .taxConfiguration(configuration)
                .taxType(taxType)
                .taxRate(new BigDecimal("9.0000"))
                .applicabilityType(TaxApplicabilityType.SAME_JURISDICTION)
                .isActive(true)
                .build();

        requestDto = TaxConfigurationComponentRequestDto.builder()
                .taxTypeId(taxTypeId)
                .taxRate(new BigDecimal("9.0000"))
                .applicabilityType(TaxApplicabilityType.SAME_JURISDICTION)
                .build();
    }

    @Test
    void createComponent_nonexistentConfiguration_throwsResourceNotFoundException() {
        when(taxConfigurationRepository.findById(configId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> componentService.createComponent(configId, requestDto))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessageContaining("Tax configuration not found with ID");
    }

    @Test
    void createComponent_inactiveConfiguration_throwsValidationException() {
        configuration.setIsActive(false);
        when(taxConfigurationRepository.findById(configId)).thenReturn(Optional.of(configuration));

        assertThatThrownBy(() -> componentService.createComponent(configId, requestDto))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Cannot add component to an inactive tax configuration");
    }

    @Test
    void createComponent_nonexistentTaxType_throwsResourceNotFoundException() {
        when(taxConfigurationRepository.findById(configId)).thenReturn(Optional.of(configuration));
        when(taxTypeMasterRepository.findById(taxTypeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> componentService.createComponent(configId, requestDto))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessageContaining("Tax type not found with ID");
    }

    @Test
    void createComponent_inactiveTaxType_throwsValidationException() {
        taxType.setIsActive(false);
        when(taxConfigurationRepository.findById(configId)).thenReturn(Optional.of(configuration));
        when(taxTypeMasterRepository.findById(taxTypeId)).thenReturn(Optional.of(taxType));

        assertThatThrownBy(() -> componentService.createComponent(configId, requestDto))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Cannot use an inactive tax type");
    }

    @Test
    void createComponent_duplicateTaxType_throwsDuplicateResourceException() {
        when(taxConfigurationRepository.findById(configId)).thenReturn(Optional.of(configuration));
        when(taxTypeMasterRepository.findById(taxTypeId)).thenReturn(Optional.of(taxType));
        when(componentRepository.existsByTaxConfigurationTaxConfigurationIdAndTaxTypeTaxTypeId(configId, taxTypeId))
                .thenReturn(true);

        assertThatThrownBy(() -> componentService.createComponent(configId, requestDto))
                .isInstanceOf(GlobalExceptionHandler.DuplicateResourceException.class)
                .hasMessageContaining("already exists in this tax configuration");
    }

    @Test
    void createComponent_negativeTaxRate_throwsValidationException() {
        requestDto.setTaxRate(new BigDecimal("-5.00"));
        when(taxConfigurationRepository.findById(configId)).thenReturn(Optional.of(configuration));
        when(taxTypeMasterRepository.findById(taxTypeId)).thenReturn(Optional.of(taxType));
        when(componentRepository.existsByTaxConfigurationTaxConfigurationIdAndTaxTypeTaxTypeId(configId, taxTypeId))
                .thenReturn(false);

        assertThatThrownBy(() -> componentService.createComponent(configId, requestDto))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Tax rate cannot be negative");
    }

    @Test
    void createComponent_taxRateExceeds100_throwsValidationException() {
        requestDto.setTaxRate(new BigDecimal("105.00"));
        when(taxConfigurationRepository.findById(configId)).thenReturn(Optional.of(configuration));
        when(taxTypeMasterRepository.findById(taxTypeId)).thenReturn(Optional.of(taxType));
        when(componentRepository.existsByTaxConfigurationTaxConfigurationIdAndTaxTypeTaxTypeId(configId, taxTypeId))
                .thenReturn(false);

        assertThatThrownBy(() -> componentService.createComponent(configId, requestDto))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Tax rate cannot exceed 100%");
    }

    @Test
    void updateComponent_nonexistentComponent_throwsResourceNotFoundException() {
        when(componentRepository.findById(componentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> componentService.updateComponent(componentId, requestDto))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessageContaining("Tax configuration component not found with ID");
    }

    @Test
    void getComponentById_nonexistentComponent_throwsResourceNotFoundException() {
        when(componentRepository.findById(componentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> componentService.getComponentById(componentId))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessageContaining("Tax configuration component not found with ID");
    }

    @Test
    void getComponentsByConfiguration_nonexistentConfiguration_throwsResourceNotFoundException() {
        when(taxConfigurationRepository.existsById(configId)).thenReturn(false);

        assertThatThrownBy(() -> componentService.getComponentsByConfiguration(configId))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessageContaining("Tax configuration not found with ID");
    }

    @Test
    void deactivateComponent_nonexistentComponent_throwsResourceNotFoundException() {
        when(componentRepository.findById(componentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> componentService.deactivateComponent(componentId))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessageContaining("Tax configuration component not found with ID");
    }
}
