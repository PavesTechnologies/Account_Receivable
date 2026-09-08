package com.AccountReceivableManagement.service_Imple.tax_calculation;

import com.AccountReceivableManagement.dto.tax_calculation.TaxTypeRequestDto;
import com.AccountReceivableManagement.dto.tax_calculation.TaxTypeResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxTypeMaster;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxTypeMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxTypeMasterServiceImplTest {

    @Mock
    private TaxTypeMasterRepository taxTypeMasterRepository;

    @InjectMocks
    private TaxTypeMasterServiceImpl taxTypeMasterService;

    private UUID taxTypeId;
    private TaxTypeMaster taxType;
    private TaxTypeRequestDto requestDto;

    @BeforeEach
    void setUp() {
        taxTypeId = UUID.randomUUID();
        taxType = TaxTypeMaster.builder()
                .taxTypeId(taxTypeId)
                .taxTypeCode("GST")
                .taxTypeName("Goods and Services Tax")
                .description("Standard GST")
                .isActive(true)
                .build();

        requestDto = TaxTypeRequestDto.builder()
                .taxTypeCode("GST")
                .taxTypeName("Goods and Services Tax")
                .description("Standard GST")
                .build();
    }

    @Test
    void createTaxType_validRequest_createsSuccessfully() {
        when(taxTypeMasterRepository.existsByTaxTypeCodeIgnoreCase("GST")).thenReturn(false);
        when(taxTypeMasterRepository.save(any(TaxTypeMaster.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaxTypeResponseDto response = taxTypeMasterService.createTaxType(requestDto);

        assertThat(response.getTaxTypeCode()).isEqualTo("GST");
        assertThat(response.getTaxTypeName()).isEqualTo("Goods and Services Tax");
        assertThat(response.getIsActive()).isTrue();
    }

    @Test
    void createTaxType_duplicateCode_throwsDuplicateResourceException() {
        when(taxTypeMasterRepository.existsByTaxTypeCodeIgnoreCase("GST")).thenReturn(true);

        assertThatThrownBy(() -> taxTypeMasterService.createTaxType(requestDto))
                .isInstanceOf(GlobalExceptionHandler.DuplicateResourceException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateTaxType_nonexistentId_throwsResourceNotFoundException() {
        when(taxTypeMasterRepository.findById(taxTypeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taxTypeMasterService.updateTaxType(taxTypeId, requestDto))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessageContaining("Tax type not found with ID");
    }

    @Test
    void updateTaxType_duplicateCode_throwsDuplicateResourceException() {
        when(taxTypeMasterRepository.findById(taxTypeId)).thenReturn(Optional.of(taxType));
        when(taxTypeMasterRepository.existsByTaxTypeCodeIgnoreCaseAndTaxTypeIdNot("GST", taxTypeId)).thenReturn(true);

        assertThatThrownBy(() -> taxTypeMasterService.updateTaxType(taxTypeId, requestDto))
                .isInstanceOf(GlobalExceptionHandler.DuplicateResourceException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void getTaxTypeById_nonexistentId_throwsResourceNotFoundException() {
        when(taxTypeMasterRepository.findById(taxTypeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taxTypeMasterService.getTaxTypeById(taxTypeId))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessageContaining("Tax type not found with ID");
    }

    @Test
    void deactivateTaxType_nonexistentId_throwsResourceNotFoundException() {
        when(taxTypeMasterRepository.findById(taxTypeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taxTypeMasterService.deactivateTaxType(taxTypeId))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessageContaining("Tax type not found with ID");
    }
}
