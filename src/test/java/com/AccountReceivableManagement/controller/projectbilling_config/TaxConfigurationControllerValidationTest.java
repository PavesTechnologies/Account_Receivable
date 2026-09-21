package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationComponentRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationComponentResponseDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationResponseDto;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxApplicabilityType;
import com.AccountReceivableManagement.service_interface.projectbilling_config.TaxConfigurationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that Bean Validation failures on TaxConfigurationRequestDto
 * are surfaced as HTTP 400 (via GlobalExceptionHandler's
 * MethodArgumentNotValidException handler) instead of falling through
 * to the generic HTTP 500 handler.
 */
@WebMvcTest(TaxConfigurationController.class)
class TaxConfigurationControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaxConfigurationService taxConfigurationService;

    private TaxConfigurationComponentRequestDto validComponent() {
        return TaxConfigurationComponentRequestDto.builder()
                .taxTypeId(UUID.randomUUID())
                .taxRate(new BigDecimal("9.0000"))
                .applicabilityType(TaxApplicabilityType.ALL)
                .build();
    }

    @Test
    void create_missingComponents_returnsBadRequest() throws Exception {
        TaxConfigurationRequestDto request = TaxConfigurationRequestDto.builder()
                .taxRegionId(UUID.randomUUID())
                .taxRegime("GST")
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .effectiveTo(LocalDate.of(2026, 12, 31))
                .components(null)
                .build();

        mockMvc.perform(post("/api/v1/tax-rate-configurations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("At least one tax component is required."));
    }

    @Test
    void create_missingTaxRegionId_returnsBadRequest() throws Exception {
        TaxConfigurationRequestDto request = TaxConfigurationRequestDto.builder()
                .taxRegionId(null)
                .taxRegime("GST")
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .effectiveTo(LocalDate.of(2026, 12, 31))
                .components(List.of(validComponent()))
                .build();

        mockMvc.perform(post("/api/v1/tax-rate-configurations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Tax region is required."));
    }

    @Test
    void create_blankTaxRegime_returnsBadRequest() throws Exception {
        TaxConfigurationRequestDto request = TaxConfigurationRequestDto.builder()
                .taxRegionId(UUID.randomUUID())
                .taxRegime("")
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .effectiveTo(LocalDate.of(2026, 12, 31))
                .components(List.of(validComponent()))
                .build();

        mockMvc.perform(post("/api/v1/tax-rate-configurations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Tax regime is required."));
    }

    @Test
    void create_validRequest_returnsCreated() throws Exception {
        TaxConfigurationRequestDto request = TaxConfigurationRequestDto.builder()
                .taxRegionId(UUID.randomUUID())
                .taxRegime("GST")
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .effectiveTo(LocalDate.of(2026, 12, 31))
                .components(List.of(validComponent()))
                .build();

        TaxConfigurationResponseDto response = TaxConfigurationResponseDto.builder()
                .taxConfigurationId(UUID.randomUUID())
                .taxRegionId(request.getTaxRegionId())
                .taxRegime("GST")
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .isActive(true)
                .components(List.of(TaxConfigurationComponentResponseDto.builder()
                        .taxConfigurationComponentId(UUID.randomUUID())
                        .taxTypeId(request.getComponents().get(0).getTaxTypeId())
                        .taxRate(request.getComponents().get(0).getTaxRate())
                        .applicabilityType(TaxApplicabilityType.ALL)
                        .isActive(true)
                        .build()))
                .build();

        when(taxConfigurationService.create(any(TaxConfigurationRequestDto.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/tax-rate-configurations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Tax Rate Configuration created successfully."));
    }
}
