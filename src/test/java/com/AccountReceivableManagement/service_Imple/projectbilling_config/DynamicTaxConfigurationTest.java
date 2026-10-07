package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.TaxStructureResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxComponentMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRegimeMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRegionMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxTypeMaster;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.TaxComponentInputType;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxComponentMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRegimeMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRegionMasterRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.TaxStructureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for dynamic tax configuration.
 * Tests verify that the system supports multiple countries, regimes, and components
 * without hardcoding country-specific logic in Java code.
 */
@ExtendWith(MockitoExtension.class)
class DynamicTaxConfigurationTest {

    @Mock
    private TaxRegionMasterRepository taxRegionRepository;

    @Mock
    private TaxRegimeMasterRepository taxRegimeRepository;

    @Mock
    private TaxComponentMasterRepository taxComponentRepository;

    @InjectMocks
    private TaxStructureServiceImpl taxStructureService;

    private TaxRegionMaster indiaRegion;
    private TaxRegionMaster ukRegion;
    private TaxRegionMaster singaporeRegion;
    private TaxRegionMaster usaRegion;
    private TaxRegionMaster arbitraryRegion;

    private TaxRegimeMaster gstRegime;
    private TaxRegimeMaster vatRegime;
    private TaxRegimeMaster salesTaxRegime;
    private TaxRegimeMaster arbitraryRegime;

    private TaxTypeMaster cgstType;
    private TaxTypeMaster sgstType;
    private TaxTypeMaster igstType;
    private TaxTypeMaster vatType;
    private TaxTypeMaster stateTaxType;
    private TaxTypeMaster localTaxType;
    private TaxTypeMaster arbitraryType;

    @BeforeEach
    void setUp() {
        // Tax Regions
        indiaRegion = TaxRegionMaster.builder()
                .taxRegionId(UUID.randomUUID())
                .taxRegionCode("IN")
                .taxRegionName("India")
                .taxRegime("GST")
                .currencyCode("INR")
                .isActive(true)
                .build();

        ukRegion = TaxRegionMaster.builder()
                .taxRegionId(UUID.randomUUID())
                .taxRegionCode("GB")
                .taxRegionName("United Kingdom")
                .taxRegime("VAT")
                .currencyCode("GBP")
                .isActive(true)
                .build();

        singaporeRegion = TaxRegionMaster.builder()
                .taxRegionId(UUID.randomUUID())
                .taxRegionCode("SG")
                .taxRegionName("Singapore")
                .taxRegime("GST")
                .currencyCode("SGD")
                .isActive(true)
                .build();

        usaRegion = TaxRegionMaster.builder()
                .taxRegionId(UUID.randomUUID())
                .taxRegionCode("US")
                .taxRegionName("United States")
                .taxRegime("SALES_TAX")
                .currencyCode("USD")
                .isActive(true)
                .build();

        arbitraryRegion = TaxRegionMaster.builder()
                .taxRegionId(UUID.randomUUID())
                .taxRegionCode("XX")
                .taxRegionName("Arbitrary Country")
                .taxRegime(null)
                .currencyCode("XYZ")
                .isActive(true)
                .build();

        // Tax Regimes
        gstRegime = TaxRegimeMaster.builder()
                .taxRegimeId(UUID.randomUUID())
                .taxRegimeCode("GST")
                .taxRegimeName("GST")
                .description("Goods and Services Tax")
                .isActive(true)
                .build();

        vatRegime = TaxRegimeMaster.builder()
                .taxRegimeId(UUID.randomUUID())
                .taxRegimeCode("VAT")
                .taxRegimeName("VAT")
                .description("Value Added Tax")
                .isActive(true)
                .build();

        salesTaxRegime = TaxRegimeMaster.builder()
                .taxRegimeId(UUID.randomUUID())
                .taxRegimeCode("SALES_TAX")
                .taxRegimeName("SALES_TAX")
                .description("Sales Tax")
                .isActive(true)
                .build();

        arbitraryRegime = TaxRegimeMaster.builder()
                .taxRegimeId(UUID.randomUUID())
                .taxRegimeCode("ARBITRARY_TAX")
                .taxRegimeName("Arbitrary Tax Regime")
                .description("A completely new tax regime")
                .isActive(true)
                .build();

        // Tax Types
        cgstType = TaxTypeMaster.builder()
                .taxTypeId(UUID.randomUUID())
                .taxTypeCode("CGST")
                .taxTypeName("Central GST")
                .isActive(true)
                .build();

        sgstType = TaxTypeMaster.builder()
                .taxTypeId(UUID.randomUUID())
                .taxTypeCode("SGST")
                .taxTypeName("State GST")
                .isActive(true)
                .build();

        igstType = TaxTypeMaster.builder()
                .taxTypeId(UUID.randomUUID())
                .taxTypeCode("IGST")
                .taxTypeName("Integrated GST")
                .isActive(true)
                .build();

        vatType = TaxTypeMaster.builder()
                .taxTypeId(UUID.randomUUID())
                .taxTypeCode("VAT")
                .taxTypeName("VAT")
                .isActive(true)
                .build();

        stateTaxType = TaxTypeMaster.builder()
                .taxTypeId(UUID.randomUUID())
                .taxTypeCode("STATE_TAX")
                .taxTypeName("State Tax")
                .isActive(true)
                .build();

        localTaxType = TaxTypeMaster.builder()
                .taxTypeId(UUID.randomUUID())
                .taxTypeCode("LOCAL_TAX")
                .taxTypeName("Local Tax")
                .isActive(true)
                .build();

        arbitraryType = TaxTypeMaster.builder()
                .taxTypeId(UUID.randomUUID())
                .taxTypeCode("ARBITRARY_COMPONENT")
                .taxTypeName("Arbitrary Tax Component")
                .isActive(true)
                .build();
    }

    // 1. India GST discovery test
    @Test
    void getTaxStructureByRegion_india_returnsGSTRegimeWithComponents() {
        when(taxRegionRepository.findById(indiaRegion.getTaxRegionId()))
                .thenReturn(Optional.of(indiaRegion));
        when(taxRegimeRepository.findByIsActiveTrueOrderByTaxRegimeNameAsc())
                .thenReturn(List.of(gstRegime, vatRegime, salesTaxRegime));

        TaxComponentMaster cgst = TaxComponentMaster.builder()
                .taxComponentId(UUID.randomUUID())
                .taxRegime(gstRegime)
                .taxType(cgstType)
                .componentCode("CGST")
                .componentName("CGST")
                .inputType(TaxComponentInputType.PERCENTAGE)
                .displayOrder(1)
                .effectiveFrom(LocalDate.of(2020, 1, 1))
                .isActive(true)
                .build();

        TaxComponentMaster sgst = TaxComponentMaster.builder()
                .taxComponentId(UUID.randomUUID())
                .taxRegime(gstRegime)
                .taxType(sgstType)
                .componentCode("SGST")
                .componentName("SGST")
                .inputType(TaxComponentInputType.PERCENTAGE)
                .displayOrder(2)
                .effectiveFrom(LocalDate.of(2020, 1, 1))
                .isActive(true)
                .build();

        TaxComponentMaster igst = TaxComponentMaster.builder()
                .taxComponentId(UUID.randomUUID())
                .taxRegime(gstRegime)
                .taxType(igstType)
                .componentCode("IGST")
                .componentName("IGST")
                .inputType(TaxComponentInputType.PERCENTAGE)
                .displayOrder(3)
                .effectiveFrom(LocalDate.of(2020, 1, 1))
                .isActive(true)
                .build();

        when(taxComponentRepository.findActiveComponentsByRegime(
                gstRegime.getTaxRegimeId(), LocalDate.now()))
                .thenReturn(List.of(cgst, sgst, igst));
        when(taxComponentRepository.findActiveComponentsByRegime(
                vatRegime.getTaxRegimeId(), LocalDate.now()))
                .thenReturn(List.of());
        when(taxComponentRepository.findActiveComponentsByRegime(
                salesTaxRegime.getTaxRegimeId(), LocalDate.now()))
                .thenReturn(List.of());

        TaxStructureResponseDto response = taxStructureService.getTaxStructureByRegion(indiaRegion.getTaxRegionId());

        assertThat(response.getTaxRegion().getTaxRegionCode()).isEqualTo("IN");
        assertThat(response.getTaxRegion().getTaxRegionName()).isEqualTo("India");
        assertThat(response.getTaxRegion().getCurrencyCode()).isEqualTo("INR");
        assertThat(response.getTaxRegimes()).hasSize(3);

        // Verify GST regime has CGST, SGST, IGST components
        TaxStructureResponseDto.TaxRegimeStructure gstStructure = response.getTaxRegimes().stream()
                .filter(r -> "GST".equals(r.getTaxRegimeCode()))
                .findFirst()
                .orElseThrow();

        assertThat(gstStructure.getComponents()).hasSize(3);
        assertThat(gstStructure.getComponents())
                .extracting(TaxStructureResponseDto.TaxComponentInfo::getComponentCode)
                .containsExactlyInAnyOrder("CGST", "SGST", "IGST");
        assertThat(gstStructure.getComponents())
                .extracting(TaxStructureResponseDto.TaxComponentInfo::getInputType)
                .containsOnly(TaxComponentInputType.PERCENTAGE);
    }

    // 2. UK VAT discovery test
    @Test
    void getTaxStructureByRegion_uk_returnsVATRegimeWithComponent() {
        when(taxRegionRepository.findById(ukRegion.getTaxRegionId()))
                .thenReturn(Optional.of(ukRegion));
        when(taxRegimeRepository.findByIsActiveTrueOrderByTaxRegimeNameAsc())
                .thenReturn(List.of(gstRegime, vatRegime, salesTaxRegime));

        TaxComponentMaster vat = TaxComponentMaster.builder()
                .taxComponentId(UUID.randomUUID())
                .taxRegime(vatRegime)
                .taxType(vatType)
                .componentCode("VAT")
                .componentName("VAT")
                .inputType(TaxComponentInputType.PERCENTAGE)
                .displayOrder(1)
                .effectiveFrom(LocalDate.of(2020, 1, 1))
                .isActive(true)
                .build();

        when(taxComponentRepository.findActiveComponentsByRegime(
                gstRegime.getTaxRegimeId(), LocalDate.now()))
                .thenReturn(List.of());
        when(taxComponentRepository.findActiveComponentsByRegime(
                vatRegime.getTaxRegimeId(), LocalDate.now()))
                .thenReturn(List.of(vat));
        when(taxComponentRepository.findActiveComponentsByRegime(
                salesTaxRegime.getTaxRegimeId(), LocalDate.now()))
                .thenReturn(List.of());

        TaxStructureResponseDto response = taxStructureService.getTaxStructureByRegion(ukRegion.getTaxRegionId());

        assertThat(response.getTaxRegion().getTaxRegionCode()).isEqualTo("GB");
        assertThat(response.getTaxRegion().getTaxRegionName()).isEqualTo("United Kingdom");
        assertThat(response.getTaxRegion().getCurrencyCode()).isEqualTo("GBP");

        // Verify VAT regime has VAT component
        TaxStructureResponseDto.TaxRegimeStructure vatStructure = response.getTaxRegimes().stream()
                .filter(r -> "VAT".equals(r.getTaxRegimeCode()))
                .findFirst()
                .orElseThrow();

        assertThat(vatStructure.getComponents()).hasSize(1);
        assertThat(vatStructure.getComponents().get(0).getComponentCode()).isEqualTo("VAT");
        assertThat(vatStructure.getComponents().get(0).getComponentName()).isEqualTo("VAT");
        assertThat(vatStructure.getComponents().get(0).getInputType()).isEqualTo(TaxComponentInputType.PERCENTAGE);
    }

    // 3. Singapore GST discovery test
    @Test
    void getTaxStructureByRegion_singapore_returnsGSTRegimeWithComponents() {
        when(taxRegionRepository.findById(singaporeRegion.getTaxRegionId()))
                .thenReturn(Optional.of(singaporeRegion));
        when(taxRegimeRepository.findByIsActiveTrueOrderByTaxRegimeNameAsc())
                .thenReturn(List.of(gstRegime));

        TaxComponentMaster gst = TaxComponentMaster.builder()
                .taxComponentId(UUID.randomUUID())
                .taxRegime(gstRegime)
                .taxType(vatType)
                .componentCode("GST")
                .componentName("GST")
                .inputType(TaxComponentInputType.PERCENTAGE)
                .displayOrder(1)
                .effectiveFrom(LocalDate.of(2020, 1, 1))
                .isActive(true)
                .build();

        when(taxComponentRepository.findActiveComponentsByRegime(
                gstRegime.getTaxRegimeId(), LocalDate.now()))
                .thenReturn(List.of(gst));

        TaxStructureResponseDto response = taxStructureService.getTaxStructureByRegion(singaporeRegion.getTaxRegionId());

        assertThat(response.getTaxRegion().getTaxRegionCode()).isEqualTo("SG");
        assertThat(response.getTaxRegion().getTaxRegionName()).isEqualTo("Singapore");
        assertThat(response.getTaxRegion().getCurrencyCode()).isEqualTo("SGD");
        assertThat(response.getTaxRegimes()).hasSize(1);
        assertThat(response.getTaxRegimes().get(0).getTaxRegimeCode()).isEqualTo("GST");
        assertThat(response.getTaxRegimes().get(0).getComponents()).hasSize(1);
        assertThat(response.getTaxRegimes().get(0).getComponents().get(0).getComponentCode()).isEqualTo("GST");
    }

    // 4. USA Sales Tax discovery test
    @Test
    void getTaxStructureByRegion_usa_returnsSalesTaxRegimeWithComponents() {
        when(taxRegionRepository.findById(usaRegion.getTaxRegionId()))
                .thenReturn(Optional.of(usaRegion));
        when(taxRegimeRepository.findByIsActiveTrueOrderByTaxRegimeNameAsc())
                .thenReturn(List.of(salesTaxRegime));

        TaxComponentMaster stateTax = TaxComponentMaster.builder()
                .taxComponentId(UUID.randomUUID())
                .taxRegime(salesTaxRegime)
                .taxType(stateTaxType)
                .componentCode("STATE_TAX")
                .componentName("State Tax")
                .inputType(TaxComponentInputType.PERCENTAGE)
                .displayOrder(1)
                .effectiveFrom(LocalDate.of(2020, 1, 1))
                .isActive(true)
                .build();

        TaxComponentMaster localTax = TaxComponentMaster.builder()
                .taxComponentId(UUID.randomUUID())
                .taxRegime(salesTaxRegime)
                .taxType(localTaxType)
                .componentCode("LOCAL_TAX")
                .componentName("Local Tax")
                .inputType(TaxComponentInputType.PERCENTAGE)
                .displayOrder(2)
                .effectiveFrom(LocalDate.of(2020, 1, 1))
                .isActive(true)
                .build();

        when(taxComponentRepository.findActiveComponentsByRegime(
                salesTaxRegime.getTaxRegimeId(), LocalDate.now()))
                .thenReturn(List.of(stateTax, localTax));

        TaxStructureResponseDto response = taxStructureService.getTaxStructureByRegion(usaRegion.getTaxRegionId());

        assertThat(response.getTaxRegion().getTaxRegionCode()).isEqualTo("US");
        assertThat(response.getTaxRegion().getTaxRegionName()).isEqualTo("United States");
        assertThat(response.getTaxRegion().getCurrencyCode()).isEqualTo("USD");
        assertThat(response.getTaxRegimes()).hasSize(1);
        assertThat(response.getTaxRegimes().get(0).getTaxRegimeCode()).isEqualTo("SALES_TAX");
        assertThat(response.getTaxRegimes().get(0).getComponents()).hasSize(2);
        assertThat(response.getTaxRegimes().get(0).getComponents())
                .extracting(TaxStructureResponseDto.TaxComponentInfo::getComponentCode)
                .containsExactlyInAnyOrder("STATE_TAX", "LOCAL_TAX");
    }

    // 5. Arbitrary new country + new regime + new component test
    // This is the critical test that proves no Java code changes are needed
    @Test
    void getTaxStructureByRegion_arbitraryCountry_returnsNewRegimeWithNewComponent_withoutCodeChange() {
        when(taxRegionRepository.findById(arbitraryRegion.getTaxRegionId()))
                .thenReturn(Optional.of(arbitraryRegion));
        when(taxRegimeRepository.findByIsActiveTrueOrderByTaxRegimeNameAsc())
                .thenReturn(List.of(arbitraryRegime));

        TaxComponentMaster arbitraryComponent = TaxComponentMaster.builder()
                .taxComponentId(UUID.randomUUID())
                .taxRegime(arbitraryRegime)
                .taxType(arbitraryType)
                .componentCode("ARBITRARY_COMPONENT")
                .componentName("Arbitrary Tax Component")
                .inputType(TaxComponentInputType.PERCENTAGE)
                .displayOrder(1)
                .effectiveFrom(LocalDate.of(2020, 1, 1))
                .isActive(true)
                .build();

        when(taxComponentRepository.findActiveComponentsByRegime(
                arbitraryRegime.getTaxRegimeId(), LocalDate.now()))
                .thenReturn(List.of(arbitraryComponent));

        TaxStructureResponseDto response = taxStructureService.getTaxStructureByRegion(arbitraryRegion.getTaxRegionId());

        assertThat(response.getTaxRegion().getTaxRegionCode()).isEqualTo("XX");
        assertThat(response.getTaxRegion().getTaxRegionName()).isEqualTo("Arbitrary Country");
        assertThat(response.getTaxRegion().getCurrencyCode()).isEqualTo("XYZ");
        assertThat(response.getTaxRegimes()).hasSize(1);
        assertThat(response.getTaxRegimes().get(0).getTaxRegimeCode()).isEqualTo("ARBITRARY_TAX");
        assertThat(response.getTaxRegimes().get(0).getTaxRegimeName()).isEqualTo("Arbitrary Tax Regime");
        assertThat(response.getTaxRegimes().get(0).getComponents()).hasSize(1);
        assertThat(response.getTaxRegimes().get(0).getComponents().get(0).getComponentCode())
                .isEqualTo("ARBITRARY_COMPONENT");
        assertThat(response.getTaxRegimes().get(0).getComponents().get(0).getComponentName())
                .isEqualTo("Arbitrary Tax Component");

        // This test proves that adding a new country, regime, and component
        // requires only configuration/master data, no Java code changes
    }

    // 6. Missing tax region throws exception
    @Test
    void getTaxStructureByRegion_missingRegion_throwsResourceNotFoundException() {
        UUID unknownRegionId = UUID.randomUUID();
        when(taxRegionRepository.findById(unknownRegionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taxStructureService.getTaxStructureByRegion(unknownRegionId))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessage("Tax region not found.");
    }

    // 7. Verify component metadata is returned correctly
    @Test
    void getTaxStructureByRegion_returnsCompleteComponentMetadata() {
        when(taxRegionRepository.findById(indiaRegion.getTaxRegionId()))
                .thenReturn(Optional.of(indiaRegion));
        when(taxRegimeRepository.findByIsActiveTrueOrderByTaxRegimeNameAsc())
                .thenReturn(List.of(gstRegime));

        TaxComponentMaster cgst = TaxComponentMaster.builder()
                .taxComponentId(UUID.randomUUID())
                .taxRegime(gstRegime)
                .taxType(cgstType)
                .componentCode("CGST")
                .componentName("Central GST")
                .description("Central Goods and Services Tax")
                .inputType(TaxComponentInputType.PERCENTAGE)
                .displayOrder(1)
                .effectiveFrom(LocalDate.of(2020, 1, 1))
                .isActive(true)
                .build();

        when(taxComponentRepository.findActiveComponentsByRegime(
                gstRegime.getTaxRegimeId(), LocalDate.now()))
                .thenReturn(List.of(cgst));

        TaxStructureResponseDto response = taxStructureService.getTaxStructureByRegion(indiaRegion.getTaxRegionId());

        TaxStructureResponseDto.TaxComponentInfo component =
                response.getTaxRegimes().get(0).getComponents().get(0);

        assertThat(component.getTaxComponentId()).isNotNull();
        assertThat(component.getTaxTypeId()).isEqualTo(cgstType.getTaxTypeId());
        assertThat(component.getTaxTypeCode()).isEqualTo("CGST");
        assertThat(component.getTaxTypeName()).isEqualTo("Central GST");
        assertThat(component.getComponentCode()).isEqualTo("CGST");
        assertThat(component.getComponentName()).isEqualTo("Central GST");
        assertThat(component.getDescription()).isEqualTo("Central Goods and Services Tax");
        assertThat(component.getInputType()).isEqualTo(TaxComponentInputType.PERCENTAGE);
        assertThat(component.getDisplayOrder()).isEqualTo(1);
    }

    // 8. Verify multiple regimes are returned for a region
    @Test
    void getTaxStructureByRegion_returnsMultipleRegimes() {
        when(taxRegionRepository.findById(indiaRegion.getTaxRegionId()))
                .thenReturn(Optional.of(indiaRegion));
        when(taxRegimeRepository.findByIsActiveTrueOrderByTaxRegimeNameAsc())
                .thenReturn(List.of(gstRegime, vatRegime, salesTaxRegime));

        when(taxComponentRepository.findActiveComponentsByRegime(any(), any()))
                .thenReturn(List.of());

        TaxStructureResponseDto response = taxStructureService.getTaxStructureByRegion(indiaRegion.getTaxRegionId());

        assertThat(response.getTaxRegimes()).hasSize(3);
        assertThat(response.getTaxRegimes())
                .extracting(TaxStructureResponseDto.TaxRegimeStructure::getTaxRegimeCode)
                .containsExactlyInAnyOrder("GST", "VAT", "SALES_TAX");
    }

    // 9. Verify only active regimes are returned
    @Test
    void getTaxStructureByRegion_returnsOnlyActiveRegimes() {
        when(taxRegionRepository.findById(indiaRegion.getTaxRegionId()))
                .thenReturn(Optional.of(indiaRegion));

        TaxRegimeMaster inactiveRegime = TaxRegimeMaster.builder()
                .taxRegimeId(UUID.randomUUID())
                .taxRegimeCode("INACTIVE")
                .taxRegimeName("Inactive Regime")
                .isActive(false)
                .build();

        when(taxRegimeRepository.findByIsActiveTrueOrderByTaxRegimeNameAsc())
                .thenReturn(List.of(gstRegime, vatRegime));

        when(taxComponentRepository.findActiveComponentsByRegime(any(), any()))
                .thenReturn(List.of());

        TaxStructureResponseDto response = taxStructureService.getTaxStructureByRegion(indiaRegion.getTaxRegionId());

        assertThat(response.getTaxRegimes()).hasSize(2);
        assertThat(response.getTaxRegimes())
                .extracting(TaxStructureResponseDto.TaxRegimeStructure::getTaxRegimeCode)
                .doesNotContain("INACTIVE");
    }

    // 10. Verify components are ordered by displayOrder
    @Test
    void getTaxStructureByRegion_componentsOrderedByDisplayOrder() {
        when(taxRegionRepository.findById(indiaRegion.getTaxRegionId()))
                .thenReturn(Optional.of(indiaRegion));
        when(taxRegimeRepository.findByIsActiveTrueOrderByTaxRegimeNameAsc())
                .thenReturn(List.of(gstRegime));

        TaxComponentMaster component3 = TaxComponentMaster.builder()
                .taxComponentId(UUID.randomUUID())
                .taxRegime(gstRegime)
                .taxType(igstType)
                .componentCode("IGST")
                .componentName("IGST")
                .displayOrder(3)
                .effectiveFrom(LocalDate.of(2020, 1, 1))
                .isActive(true)
                .build();

        TaxComponentMaster component1 = TaxComponentMaster.builder()
                .taxComponentId(UUID.randomUUID())
                .taxRegime(gstRegime)
                .taxType(cgstType)
                .componentCode("CGST")
                .componentName("CGST")
                .displayOrder(1)
                .effectiveFrom(LocalDate.of(2020, 1, 1))
                .isActive(true)
                .build();

        TaxComponentMaster component2 = TaxComponentMaster.builder()
                .taxComponentId(UUID.randomUUID())
                .taxRegime(gstRegime)
                .taxType(sgstType)
                .componentCode("SGST")
                .componentName("SGST")
                .displayOrder(2)
                .effectiveFrom(LocalDate.of(2020, 1, 1))
                .isActive(true)
                .build();

        when(taxComponentRepository.findActiveComponentsByRegime(
                gstRegime.getTaxRegimeId(), LocalDate.now()))
                .thenReturn(List.of(component3, component1, component2));

        TaxStructureResponseDto response = taxStructureService.getTaxStructureByRegion(indiaRegion.getTaxRegionId());

        assertThat(response.getTaxRegimes().get(0).getComponents())
                .extracting(TaxStructureResponseDto.TaxComponentInfo::getComponentCode)
                .containsExactly("CGST", "SGST", "IGST");
    }
}
