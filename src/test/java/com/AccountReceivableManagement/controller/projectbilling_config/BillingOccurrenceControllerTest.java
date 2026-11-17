package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingOccurrenceResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingOccurrenceView;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingRecurringConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.repo.tax_calculation.TaxCalculationRepository;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingConfigurationService;
import com.AccountReceivableManagement.service_interface.tax_calculation.TaxCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingOccurrenceControllerTest {

    @Mock private BillingScheduleRepository billingScheduleRepository;
    @Mock private BillingConfigurationRepository billingConfigurationRepository;
    @Mock private BillingRecurringConfigurationRepository billingRecurringConfigurationRepository;
    @Mock private TaxCalculationRepository taxCalculationRepository;
    @Mock private BillingConfigurationService billingConfigurationService;
    @Mock private TaxCalculationService taxCalculationService;
    @Mock private InvoiceService invoiceService;

    private BillingOccurrenceController controller;
    private UUID occurrenceId;
    private UUID configurationId;
    private BillingSchedule schedule;

    @BeforeEach
    void setUp() {
        controller = new BillingOccurrenceController(
                billingScheduleRepository,
                billingConfigurationRepository,
                billingRecurringConfigurationRepository,
                taxCalculationRepository,
                billingConfigurationService,
                taxCalculationService,
                invoiceService);

        occurrenceId = UUID.randomUUID();
        configurationId = UUID.randomUUID();
        schedule = BillingSchedule.builder()
                .billingScheduleId(occurrenceId)
                .billingConfiguration(BillingConfiguration.builder()
                        .billingConfigurationId(configurationId).build())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 8, 26))
                .periodEndDate(LocalDate.of(2026, 9, 8))
                .billingAmount(new BigDecimal("2500.00"))
                .periodStatus(BillingPeriodStatus.TAX_CALCULATED)
                .taxStatus(BillingPeriodStatus.TAX_CALCULATED)
                .isActive(true)
                .build();
        lenient().when(billingScheduleRepository.findById(occurrenceId)).thenReturn(Optional.of(schedule));
        lenient().when(taxCalculationRepository.findByBillingScheduleId(any())).thenReturn(Optional.empty());
    }

    private BillingConfigurationResponseDto configuration(String countryName, String primaryLocation) {
        return BillingConfigurationResponseDto.builder()
                .clientName("Aditya Teja")
                .projectName("Card Integration")
                .currencyCode("USD")
                .primaryLocation(primaryLocation)
                .countryName(countryName)
                .countryCode(countryName == null ? null : "IN")
                .build();
    }

    @Test
    void getOccurrence_clientCountryIndia_returnsClientCountryName() {
        when(billingConfigurationService.getBillingConfiguration(configurationId))
                .thenReturn(configuration("India", null));

        BillingOccurrenceResponseDto response = controller.getOccurrence(occurrenceId).getBody();

        assertThat(response.getClientCountryName()).isEqualTo("India");
        assertThat(response.getClientCountryCode()).isEqualTo("IN");
        assertThat(response.getProjectName()).isEqualTo("Card Integration");
    }

    // The value is the client's country, never the project's primary location.
    @Test
    void getOccurrence_clientCountryComesFromClient_notProjectPrimaryLocation() {
        when(billingConfigurationService.getBillingConfiguration(configurationId))
                .thenReturn(configuration("India", "Domestic"));

        BillingOccurrenceResponseDto response = controller.getOccurrence(occurrenceId).getBody();

        assertThat(response.getClientCountryName()).isEqualTo("India");
        assertThat(response.getClientCountryName()).isNotEqualTo("Domestic");
    }

    @Test
    void getOccurrence_clientCountryNull_returnsNullNotProjectLocation() {
        when(billingConfigurationService.getBillingConfiguration(configurationId))
                .thenReturn(configuration(null, "Domestic"));

        BillingOccurrenceResponseDto response = controller.getOccurrence(occurrenceId).getBody();

        assertThat(response.getClientCountryName()).isNull();
        assertThat(response.getClientCountryCode()).isNull();
    }

    // ---------------------------------------------------------------- configuration lifecycle

    private BillingSchedule taxCalculatedOccurrenceOf(BillingConfiguration configuration) {
        return BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .billingConfiguration(configuration)
                .periodNumber(1)
                .billingAmount(new BigDecimal("1000.00"))
                .periodStatus(BillingPeriodStatus.INVOICED)
                .taxStatus(BillingPeriodStatus.TAX_CALCULATED)
                .isInvoiced(true)
                .isActive(true)
                .build();
    }

    private List<UUID> taxCalculatedIds(BillingOccurrenceView view) {
        return controller.getOccurrences(null, null, null, BillingPeriodStatus.TAX_CALCULATED, null, null, view)
                .getBody().stream().map(BillingOccurrenceResponseDto::getBillingScheduleId).toList();
    }

    @Test
    void getOccurrences_viewSeparatesDeactivatedConfigurationFromItsReplacement() {
        BillingConfiguration deactivated = BillingConfiguration.builder().billingConfigurationId(UUID.randomUUID())
                .billingStatus(BillingConfigurationStatus.INACTIVE).manuallyDeactivated(true).build();
        BillingConfiguration replacement = BillingConfiguration.builder().billingConfigurationId(UUID.randomUUID())
                .billingStatus(BillingConfigurationStatus.ACTIVE).manuallyDeactivated(false).build();
        BillingSchedule old = taxCalculatedOccurrenceOf(deactivated);
        BillingSchedule current = taxCalculatedOccurrenceOf(replacement);
        when(billingScheduleRepository.findByTaxStatusAndIsActiveTrue(BillingPeriodStatus.TAX_CALCULATED))
                .thenReturn(List.of(old, current));

        assertThat(taxCalculatedIds(null)).containsExactly(old.getBillingScheduleId(), current.getBillingScheduleId());
        assertThat(taxCalculatedIds(BillingOccurrenceView.ALL)).hasSize(2);
        assertThat(taxCalculatedIds(BillingOccurrenceView.CURRENT)).containsExactly(current.getBillingScheduleId());
        assertThat(taxCalculatedIds(BillingOccurrenceView.HISTORICAL)).containsExactly(old.getBillingScheduleId());
    }

    // Expired, not-yet-effective or re-approval-pending configurations are not deactivated.
    @Test
    void getOccurrences_currentView_keepsInactiveButNotDeactivatedConfigurations() {
        BillingConfiguration expired = BillingConfiguration.builder().billingConfigurationId(UUID.randomUUID())
                .billingStatus(BillingConfigurationStatus.EXPIRED).manuallyDeactivated(false).build();
        BillingSchedule occurrence = taxCalculatedOccurrenceOf(expired);
        when(billingScheduleRepository.findByTaxStatusAndIsActiveTrue(BillingPeriodStatus.TAX_CALCULATED))
                .thenReturn(List.of(occurrence));

        assertThat(taxCalculatedIds(BillingOccurrenceView.CURRENT)).containsExactly(occurrence.getBillingScheduleId());
        assertThat(taxCalculatedIds(BillingOccurrenceView.HISTORICAL)).isEmpty();
    }

    @Test
    void getOccurrence_exposesOwningConfigurationLifecycle() {
        schedule.setBillingConfiguration(BillingConfiguration.builder().billingConfigurationId(configurationId)
                .billingStatus(BillingConfigurationStatus.INACTIVE).manuallyDeactivated(true).build());

        BillingOccurrenceResponseDto response = controller.getOccurrence(occurrenceId).getBody();

        assertThat(response.getBillingConfigurationId()).isEqualTo(configurationId);
        assertThat(response.getBillingConfigurationStatus()).isEqualTo(BillingConfigurationStatus.INACTIVE);
        assertThat(response.getConfigurationManuallyDeactivated()).isTrue();
        // Tax progress is reported as is, independent of the configuration's state.
        assertThat(response.getTaxStatus()).isEqualTo(BillingPeriodStatus.TAX_CALCULATED);
    }
}
