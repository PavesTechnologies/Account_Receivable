package com.AccountReceivableManagement.repo.billing_data_acquisition;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the fix for the LazyInitializationException seen when
 * {@code BillingSnapshot.items} was read after the Hibernate session that
 * loaded the snapshot had already closed (e.g. on GET
 * {@code /api/v1/billing-snapshots/by-period}, and the existing-snapshot
 * branch of {@code createBillingSnapshot}). {@code items} must be fetched
 * eagerly by this query so callers can read it safely afterward.
 */
class BillingSnapshotRepositoryTest {

    @Test
    void findByProjectIdAndBillingPeriodStartAndBillingPeriodEnd_eagerlyFetchesItems() throws NoSuchMethodException {
        Method method = BillingSnapshotRepository.class.getMethod(
                "findByProjectIdAndBillingPeriodStartAndBillingPeriodEnd",
                Long.class, LocalDate.class, LocalDate.class);

        EntityGraph entityGraph = method.getAnnotation(EntityGraph.class);

        assertThat(entityGraph).isNotNull();
        assertThat(entityGraph.attributePaths()).contains("items");
    }
}
