package com.AccountReceivableManagement.service_Imple.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionSummaryDto;
import com.AccountReceivableManagement.dto.centralizeddto.PagedResponse;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingAcquisition;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingAcquisitionStatus;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingAcquisitionRepository;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTypeMasterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The paginated Billing Data Acquisition listing: how a requested
 * {@link BillingType} becomes a set of stored master names, and how a page of
 * configurations is mapped to the listing DTO.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingDataAcquisitionOverviewServiceTest {

    @Mock private BillingConfigurationRepository billingConfigurationRepository;
    @Mock private BillingAcquisitionRepository billingAcquisitionRepository;
    @Mock private BillingSnapshotRepository billingSnapshotRepository;
    @Mock private BillingTypeMasterRepository billingTypeMasterRepository;

    @InjectMocks private BillingDataAcquisitionServiceImpl service;

    private static BillingTypeMaster master(String name) {
        return BillingTypeMaster.builder().billingTypeId(UUID.randomUUID()).billingTypeName(name).build();
    }

    private static BillingConfiguration configuration(String projectCode, String billingTypeName) {
        return BillingConfiguration.builder()
                .billingConfigurationId(UUID.randomUUID())
                .project(ProjectMasterReference.builder()
                        .pmsProjectId(23L).projectName("ERP Rollout").projectCode(projectCode)
                        .startDate(LocalDate.of(2026, 3, 1)).build())
                .client(Client.builder().clientId(UUID.randomUUID()).clientName("Acme Corp").build())
                .billingType(master(billingTypeName))
                .build();
    }

    @Test
    void timeAndMaterial_matchesEveryStoredSpelling_notJustTheCanonicalOne() {
        when(billingTypeMasterRepository.findAll()).thenReturn(List.of(
                master("Timesheet Based"),
                master("Time & Material"),
                master("Time and Material"),
                master("TIME_MATERIAL"),
                master("Fixed Price"),
                master("Milestone")));
        when(billingConfigurationRepository.findAcquisitionOverviewPageByBillingTypeNames(
                any(), any(), anyCollection(), any())).thenReturn(new PageImpl<>(List.of()));

        service.getAcquisitionOverview(BillingType.TIME_AND_MATERIAL, 0, 10);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> names = ArgumentCaptor.forClass(Collection.class);
        verify(billingConfigurationRepository).findAcquisitionOverviewPageByBillingTypeNames(
                eq(ApprovalStatus.APPROVED), eq(BillingConfigurationStatus.ACTIVE), names.capture(), any());

        // Lower-cased and trimmed, to match the LOWER(TRIM(...)) comparison in the query.
        assertThat(names.getValue()).containsExactlyInAnyOrder(
                "timesheet based", "time & material", "time and material", "time_material");
    }

    @Test
    void aBillingTypeNoMasterRowMapsTo_returnsAnEmptyPageWithoutQuerying() {
        when(billingTypeMasterRepository.findAll()).thenReturn(List.of(master("Timesheet Based")));

        PagedResponse<BillingAcquisitionSummaryDto> result =
                service.getAcquisitionOverview(BillingType.RETAINER, 2, 10);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.isFirst()).isFalse();
        assertThat(result.isLast()).isTrue();

        verify(billingConfigurationRepository, never())
                .findAcquisitionOverviewPageByBillingTypeNames(any(), any(), anyCollection(), any());
    }

    @Test
    void noBillingTypeFilter_usesTheUnfilteredQuery_andPassesPageAndSizeThrough() {
        when(billingConfigurationRepository.findAcquisitionOverviewPage(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.getAcquisitionOverview(null, 3, 25);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(billingConfigurationRepository).findAcquisitionOverviewPage(
                eq(ApprovalStatus.APPROVED), eq(BillingConfigurationStatus.ACTIVE), pageable.capture());

        assertThat(pageable.getValue().getPageNumber()).isEqualTo(3);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
        // Ordering lives in the query, so the Pageable must not carry a Sort of its own.
        assertThat(pageable.getValue().getSort().isSorted()).isFalse();

        verify(billingTypeMasterRepository, never()).findAll();
    }

    @Test
    void rowsCarryTheRealProjectCode_andTheResolvedAcquisitionStatus() {
        BillingConfiguration bc = configuration("ACME-ERP-01", "Timesheet Based");
        when(billingConfigurationRepository.findAcquisitionOverviewPage(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(bc), PageRequest.of(0, 10), 1));
        when(billingAcquisitionRepository
                .findFirstByBillingConfiguration_BillingConfigurationIdOrderByCreatedAtDesc(
                        bc.getBillingConfigurationId()))
                .thenReturn(Optional.of(BillingAcquisition.builder()
                        .id(UUID.randomUUID()).status(BillingAcquisitionStatus.READY).build()));
        when(billingSnapshotRepository.findFirstByBillingConfigurationIdOrderByCreatedDateDesc(
                bc.getBillingConfigurationId())).thenReturn(Optional.empty());

        PagedResponse<BillingAcquisitionSummaryDto> result = service.getAcquisitionOverview(null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        BillingAcquisitionSummaryDto row = result.getContent().get(0);
        assertThat(row.getBillingConfigurationId()).isEqualTo(bc.getBillingConfigurationId());
        assertThat(row.getClientName()).isEqualTo("Acme Corp");
        assertThat(row.getProjectName()).isEqualTo("ERP Rollout");
        assertThat(row.getProjectCode()).isEqualTo("ACME-ERP-01");
        assertThat(row.getBillingType()).isEqualTo("Timesheet Based");
        assertThat(row.getStatus()).isEqualTo("READY");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void aProjectWithoutACodeIsReportedAsNull_notAsASynthesisedPrjString() {
        BillingConfiguration bc = configuration(null, "Timesheet Based");
        when(billingConfigurationRepository.findAcquisitionOverviewPage(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(bc), PageRequest.of(0, 10), 1));
        when(billingAcquisitionRepository
                .findFirstByBillingConfiguration_BillingConfigurationIdOrderByCreatedAtDesc(any()))
                .thenReturn(Optional.empty());
        when(billingSnapshotRepository.findFirstByBillingConfigurationIdOrderByCreatedDateDesc(any()))
                .thenReturn(Optional.empty());

        BillingAcquisitionSummaryDto row = service.getAcquisitionOverview(null, 0, 10).getContent().get(0);

        assertThat(row.getProjectCode()).isNull();
        assertThat(row.getStatus()).isEqualTo(BillingAcquisitionStatus.NOT_ACQUIRED.name());
    }
}
