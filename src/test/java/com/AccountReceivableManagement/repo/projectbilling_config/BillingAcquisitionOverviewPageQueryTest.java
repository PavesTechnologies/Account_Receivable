package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The paginated Billing Data Acquisition query against a real (in-memory H2,
 * MySQL mode) database: ordering by project start date, where undated projects
 * land, and that paging slices that order rather than reshuffling it.
 *
 * Mock-based tests cannot cover any of this - the ORDER BY lives in the JPQL.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:acquisition_overview_page;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BillingAcquisitionOverviewPageQueryTest {

    @Autowired private BillingConfigurationRepository repository;
    @Autowired private TestEntityManager em;

    private BillingTypeMaster timesheetBased;
    private BillingTypeMaster fixedPrice;
    private Client client;
    private long nextProjectId = 1L;

    @BeforeEach
    void setUp() {
        timesheetBased = em.persist(BillingTypeMaster.builder()
                .billingTypeName("Timesheet Based").isActive(true).createdAt(LocalDateTime.now()).build());
        fixedPrice = em.persist(BillingTypeMaster.builder()
                .billingTypeName("Fixed Price").isActive(true).createdAt(LocalDateTime.now()).build());
        client = em.persist(Client.builder()
                .clientId(UUID.randomUUID()).clientName("Acme Corp").build());
    }

    /** An APPROVED + ACTIVE configuration whose project starts on {@code startDate} (may be null). */
    private BillingConfiguration configuration(String projectName, LocalDate startDate, BillingTypeMaster type) {
        return configuration(projectName, startDate, type, ApprovalStatus.APPROVED, BillingConfigurationStatus.ACTIVE);
    }

    private BillingConfiguration configuration(String projectName, LocalDate startDate, BillingTypeMaster type,
                                               ApprovalStatus approvalStatus,
                                               BillingConfigurationStatus billingStatus) {
        ProjectMasterReference project = em.persist(ProjectMasterReference.builder()
                .pmsProjectId(nextProjectId++)
                .projectName(projectName)
                .projectCode(projectName.replace(" ", "-").toUpperCase())
                .startDate(startDate)
                .build());
        BillingConfiguration configuration = em.persist(BillingConfiguration.builder()
                .project(project)
                .client(client)
                .billingType(type)
                .approvalStatus(approvalStatus)
                .billingStatus(billingStatus)
                .build());
        em.flush();
        return configuration;
    }

    private List<String> projectNames(Page<BillingConfiguration> page) {
        return page.getContent().stream().map(c -> c.getProject().getProjectName()).toList();
    }

    private Page<BillingConfiguration> page(int pageNumber, int size) {
        return repository.findAcquisitionOverviewPage(
                ApprovalStatus.APPROVED, BillingConfigurationStatus.ACTIVE, PageRequest.of(pageNumber, size));
    }

    @Test
    void newestProjectStartDateComesFirst_andUndatedProjectsSortLast() {
        configuration("Oldest", LocalDate.of(2025, 1, 10), timesheetBased);
        configuration("Undated", null, timesheetBased);
        configuration("Newest", LocalDate.of(2026, 9, 1), timesheetBased);
        configuration("Middle", LocalDate.of(2026, 3, 1), timesheetBased);

        assertThat(projectNames(page(0, 10)))
                .containsExactly("Newest", "Middle", "Oldest", "Undated");
    }

    @Test
    void pagingSlicesThatOrder_withoutRepeatingOrDroppingRows() {
        configuration("Oldest", LocalDate.of(2025, 1, 10), timesheetBased);
        configuration("Undated", null, timesheetBased);
        configuration("Newest", LocalDate.of(2026, 9, 1), timesheetBased);
        configuration("Middle", LocalDate.of(2026, 3, 1), timesheetBased);

        Page<BillingConfiguration> first = page(0, 2);
        Page<BillingConfiguration> second = page(1, 2);

        assertThat(projectNames(first)).containsExactly("Newest", "Middle");
        assertThat(projectNames(second)).containsExactly("Oldest", "Undated");
        assertThat(first.getTotalElements()).isEqualTo(4);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.isFirst()).isTrue();
        assertThat(second.isLast()).isTrue();
    }

    @Test
    void tiedStartDatesKeepADeterministicOrderAcrossPages() {
        LocalDate sameDay = LocalDate.of(2026, 5, 20);
        for (int i = 0; i < 6; i++) {
            configuration("Tied " + i, sameDay, timesheetBased);
        }

        List<String> paged = java.util.stream.Stream.of(page(0, 2), page(1, 2), page(2, 2))
                .flatMap(p -> projectNames(p).stream())
                .toList();

        // The id tiebreaker must make the pages a partition of the whole set.
        assertThat(paged).hasSize(6).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(projectNames(page(0, 10)));
        // And repeating a page must return the same rows.
        assertThat(projectNames(page(1, 2))).isEqualTo(paged.subList(2, 4));
    }

    @Test
    void onlyApprovedAndActiveConfigurationsAreListed() {
        configuration("Listed", LocalDate.of(2026, 6, 1), timesheetBased);
        configuration("Draft", LocalDate.of(2026, 7, 1), timesheetBased,
                ApprovalStatus.DRAFT, BillingConfigurationStatus.ACTIVE);
        configuration("Inactive", LocalDate.of(2026, 8, 1), timesheetBased,
                ApprovalStatus.APPROVED, BillingConfigurationStatus.INACTIVE);

        assertThat(projectNames(page(0, 10))).containsExactly("Listed");
    }

    @Test
    void theBillingTypeFilterRestrictsBothTheRowsAndTheTotal() {
        configuration("TM One", LocalDate.of(2026, 9, 1), timesheetBased);
        configuration("Fixed One", LocalDate.of(2026, 10, 1), fixedPrice);
        configuration("TM Two", LocalDate.of(2026, 4, 1), timesheetBased);

        Page<BillingConfiguration> filtered = repository.findAcquisitionOverviewPageByBillingTypeNames(
                ApprovalStatus.APPROVED, BillingConfigurationStatus.ACTIVE,
                List.of("timesheet based"), PageRequest.of(0, 10));

        assertThat(projectNames(filtered)).containsExactly("TM One", "TM Two");
        // The count query must apply the same filter, or the UI pages past the end.
        assertThat(filtered.getTotalElements()).isEqualTo(2);
        assertThat(filtered.getTotalPages()).isEqualTo(1);
    }

    @Test
    void theBillingTypeFilterMatchesTheStoredNameCaseAndPaddingInsensitively() {
        em.persist(BillingTypeMaster.builder()
                .billingTypeName("  Time & Material  ").isActive(true).createdAt(LocalDateTime.now()).build());
        BillingTypeMaster padded = em.getEntityManager()
                .createQuery("SELECT b FROM BillingTypeMaster b WHERE b.billingTypeName LIKE '%Time & Material%'",
                        BillingTypeMaster.class)
                .getSingleResult();
        configuration("Padded TM", LocalDate.of(2026, 2, 1), padded);

        Page<BillingConfiguration> filtered = repository.findAcquisitionOverviewPageByBillingTypeNames(
                ApprovalStatus.APPROVED, BillingConfigurationStatus.ACTIVE,
                List.of("time & material"), PageRequest.of(0, 10));

        assertThat(projectNames(filtered)).containsExactly("Padded TM");
    }
}
