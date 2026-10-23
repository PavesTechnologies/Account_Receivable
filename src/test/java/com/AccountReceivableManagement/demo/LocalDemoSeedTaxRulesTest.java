package com.AccountReceivableManagement.demo;

import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxComponentMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxConfigurationComponent;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRegimeMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRegionMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxTypeMaster;
import com.AccountReceivableManagement.entity.tax_calculation.TaxCalculation;
import com.AccountReceivableManagement.entity.tax_calculation.TaxCalculationComponent;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxApplicabilityType;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRegionMasterRepository;
import com.AccountReceivableManagement.repo.tax_calculation.TaxCalculationRepository;
import com.AccountReceivableManagement.service_Imple.tax_calculation.TaxCalculationServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ties the demo seed's tax data to the REAL application code: the real {@code TaxConfigurationRepository} query (on an
 * in-memory H2 database that manages ONLY the tax entities) and the real {@code TaxCalculationServiceImpl}, fed with entities
 * built from the values in local_demo_seed.sql itself. Nothing is executed against MySQL and no application configuration (CDC,
 * schedulers, ...) is loaded.
 */
class LocalDemoSeedTaxRulesTest {

    @Configuration(proxyBeanMethods = false)
    static class TaxJpa {
        @Bean
        PersistenceManagedTypes taxEntitiesOnly() {
            return PersistenceManagedTypes.of(TaxConfiguration.class.getName(), TaxConfigurationComponent.class.getName(),
                    TaxRegionMaster.class.getName(), TaxRegimeMaster.class.getName(), TaxTypeMaster.class.getName(),
                    TaxComponentMaster.class.getName());
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
                    TransactionAutoConfiguration.class))
            .withUserConfiguration(TaxJpa.class)
            .withPropertyValues("spring.datasource.url=jdbc:h2:mem:seed_tax_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1",
                    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false");

    // ---------------------------------------------------------------------------------------------------------------
    // Building the tax entities from the seed's own values
    // ---------------------------------------------------------------------------------------------------------------

    /** The REAL repository interface (and its real JPQL), created directly on the H2 entity manager - no repository scanning. */
    private static TaxConfigurationRepository taxRepository(AssertableApplicationContext context) {
        EntityManager em = SharedEntityManagerCreator.createSharedEntityManager(context.getBean(EntityManagerFactory.class));
        return new JpaRepositoryFactory(em).getRepository(TaxConfigurationRepository.class);
    }

    private record Fixture(Map<String, TaxRegionMaster> regions, Map<String, TaxConfiguration> configsByRegionCode) {
    }

    private static Fixture persistTaxSetupFromSeed(AssertableApplicationContext context) throws Exception {
        List<SeedSql.Insert> inserts = SeedSql.inserts(SeedSql.read());
        TransactionTemplate tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        EntityManager em = SharedEntityManagerCreator.createSharedEntityManager(context.getBean(EntityManagerFactory.class));
        Map<String, TaxRegimeMaster> regimes = new HashMap<>();
        Map<String, TaxTypeMaster> types = new HashMap<>();
        Map<String, TaxRegionMaster> regions = new HashMap<>();
        Map<String, TaxConfiguration> configsByHex = new HashMap<>();
        Map<String, TaxConfiguration> configsByRegionCode = new HashMap<>();

        tx.executeWithoutResult(status -> {
            for (Map<String, String> r : SeedSql.rows(inserts, "tax_regime_master")) {
                TaxRegimeMaster regime = TaxRegimeMaster.builder().taxRegimeCode(SeedSql.str(r.get("tax_regime_code")))
                        .taxRegimeName(SeedSql.str(r.get("tax_regime_name"))).description(SeedSql.str(r.get("description")))
                        .isActive(SeedSql.bool(r.get("is_active"))).build();
                em.persist(regime);
                regimes.put(regime.getTaxRegimeCode(), regime);
            }
            for (Map<String, String> r : SeedSql.rows(inserts, "tax_type_master")) {
                TaxTypeMaster type = TaxTypeMaster.builder().taxTypeCode(SeedSql.str(r.get("tax_type_code")))
                        .taxTypeName(SeedSql.str(r.get("tax_type_name"))).description(SeedSql.str(r.get("description")))
                        .isActive(SeedSql.bool(r.get("is_active"))).build();
                em.persist(type);
                types.put(type.getTaxTypeCode(), type);
            }
            for (Map<String, String> r : SeedSql.rows(inserts, "tax_region_master")) {
                TaxRegionMaster region = TaxRegionMaster.builder().taxRegionCode(SeedSql.str(r.get("tax_region_code")))
                        .taxRegionName(SeedSql.str(r.get("tax_region_name"))).currencyCode(SeedSql.str(r.get("currency_code")))
                        .taxRegime(SeedSql.str(r.get("tax_regime"))).description(SeedSql.str(r.get("description")))
                        .isActive(SeedSql.bool(r.get("is_active"))).build();
                em.persist(region);
                regions.put(region.getTaxRegionCode(), region);
            }
            for (Map<String, String> r : SeedSql.rows(inserts, "tax_configuration")) {
                String regionCode = SeedSql.naturalKey(r.get("tax_region_id")).value();
                TaxConfiguration configuration = TaxConfiguration.builder().taxRegion(regions.get(regionCode))
                        .taxRegimeMaster(regimes.get(SeedSql.naturalKey(r.get("tax_regime_id")).value()))
                        .taxRegime(SeedSql.str(r.get("tax_regime"))).effectiveFrom(SeedSql.date(r.get("effective_from")))
                        .effectiveTo(SeedSql.date(r.get("effective_to"))).isActive(SeedSql.bool(r.get("is_active"))).build();
                configsByHex.put(SeedSql.uuidHex(r.get("tax_configuration_id")), configuration);
                configsByRegionCode.put(regionCode, configuration);
            }
            for (Map<String, String> r : SeedSql.rows(inserts, "tax_configuration_component")) {
                TaxConfiguration configuration = configsByHex.get(SeedSql.uuidHex(r.get("tax_configuration_id")));
                configuration.getComponents().add(TaxConfigurationComponent.builder().taxConfiguration(configuration)
                        .taxType(types.get(SeedSql.naturalKey(r.get("tax_type_id")).value())).taxRate(SeedSql.dec(r.get("tax_rate")))
                        .applicabilityType(TaxApplicabilityType.valueOf(SeedSql.str(r.get("applicability_type"))))
                        .isActive(SeedSql.bool(r.get("is_active"))).build());
            }
            configsByHex.values().forEach(em::persist); // components cascade
        });
        return new Fixture(regions, configsByRegionCode);
    }

    /** A snapshot exactly as the seed defines it (not persisted: the service reads it through a mocked repository). */
    private static BillingSnapshot snapshotFromSeed(int index, Fixture fixture) throws Exception {
        Map<String, String> s = SeedSql.rows(SeedSql.inserts(SeedSql.read()), "billing_snapshot").get(index);
        String regionCode = SeedSql.naturalKey(s.get("tax_region_id")).value();
        return BillingSnapshot.builder().id(UUID.randomUUID()).snapshotNumber(SeedSql.str(s.get("snapshot_number")))
                .billingConfigurationId(UUID.randomUUID()).projectId(SeedSql.num(s.get("project_id")))
                .taxRegionId(fixture.regions().get(regionCode).getTaxRegionId()).taxRegionCode(SeedSql.str(s.get("tax_region_code")))
                .sourceTaxJurisdictionCode(SeedSql.str(s.get("source_tax_jurisdiction_code")))
                .destinationTaxJurisdictionCode(SeedSql.str(s.get("destination_tax_jurisdiction_code")))
                .billingPeriodStart(SeedSql.date(s.get("billing_period_start"))).billingPeriodEnd(SeedSql.date(s.get("billing_period_end")))
                .status(BillingSnapshotStatus.valueOf(SeedSql.str(s.get("status"))))
                .subtotal(SeedSql.dec(s.get("subtotal"))).totalAmount(SeedSql.dec(s.get("total_amount"))).build();
    }

    private record Calculation(TaxCalculation saved, BillingSnapshot snapshot) {
    }

    /** Runs the REAL service (real tax-configuration repository, everything else mocked) inside a transaction, like the app does. */
    private static Calculation calculate(AssertableApplicationContext context, BillingSnapshot snapshot) {
        TaxCalculationRepository calculations = mock(TaxCalculationRepository.class);
        when(calculations.existsByBillingSnapshotId(any())).thenReturn(false);
        when(calculations.save(any(TaxCalculation.class))).thenAnswer(a -> a.getArgument(0));
        BillingSnapshotRepository snapshots = mock(BillingSnapshotRepository.class);
        when(snapshots.findById(snapshot.getId())).thenReturn(Optional.of(snapshot));
        when(snapshots.save(any(BillingSnapshot.class))).thenAnswer(a -> a.getArgument(0));
        BillingConfigurationRepository configurations = mock(BillingConfigurationRepository.class);
        when(configurations.findById(any())).thenReturn(Optional.of(new BillingConfiguration()));
        TaxCalculationServiceImpl service = new TaxCalculationServiceImpl(calculations, snapshots, mock(BillingScheduleRepository.class),
                taxRepository(context), mock(TaxRegionMasterRepository.class), configurations);

        new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
                .executeWithoutResult(status -> service.calculateTax(snapshot.getId()));

        ArgumentCaptor<TaxCalculation> captor = ArgumentCaptor.forClass(TaxCalculation.class);
        verify(calculations).save(captor.capture());
        return new Calculation(captor.getValue(), snapshot);
    }

    private void withSeededTax(ThrowingConsumer<Fixture, AssertableApplicationContext> test) {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            test.accept(persistTaxSetupFromSeed(context), context);
        });
    }

    @FunctionalInterface
    private interface ThrowingConsumer<A, B> {
        void accept(A a, B b) throws Exception;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The real lookup query
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    void theRealLookupFindsTheSeededConfigurationForEachSnapshotPeriod() {
        withSeededTax((fixture, context) -> {
            TaxConfigurationRepository repository = taxRepository(context);
            UUID usRegion = fixture.regions().get("DEMO-US").getTaxRegionId();
            for (int i = 0; i < 2; i++) {
                BillingSnapshot snapshot = snapshotFromSeed(i, fixture);
                List<TaxConfiguration> found = repository.findApplicableConfigurations(snapshot.getTaxRegionId(), snapshot.getBillingPeriodStart());
                assertThat(found).as("configuration for " + snapshot.getSnapshotNumber()).hasSize(1);
                assertThat(found.get(0).getTaxRegion().getTaxRegionId()).isEqualTo(usRegion);
            }
        });
    }

    @Test
    void theRealLookupRespectsRegionEffectiveDatesAndActiveFlag() {
        withSeededTax((fixture, context) -> {
            TaxConfigurationRepository repository = taxRepository(context);
            UUID us = fixture.regions().get("DEMO-US").getTaxRegionId();
            UUID in = fixture.regions().get("DEMO-IN").getTaxRegionId();
            LocalDate from = fixture.configsByRegionCode().get("DEMO-US").getEffectiveFrom();

            assertThat(repository.findApplicableConfigurations(us, from)).hasSize(1);
            assertThat(repository.findApplicableConfigurations(us, from.minusDays(1))).as("before effective_from").isEmpty();
            assertThat(repository.findApplicableConfigurations(in, from)).hasSize(1);
            assertThat(repository.findApplicableConfigurations(UUID.randomUUID(), from)).as("another region").isEmpty();

            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
                TaxConfiguration configuration = repository.findById(fixture.configsByRegionCode().get("DEMO-US").getTaxConfigurationId()).orElseThrow();
                configuration.setIsActive(false);
                repository.saveAndFlush(configuration);
            });
            assertThat(repository.findApplicableConfigurations(us, from)).as("inactive configuration").isEmpty();
        });
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The real service, fed with the seed's snapshots
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    void theRealServiceCalculatesTheDocumentedTaxForBothSeededSnapshots() {
        withSeededTax((fixture, context) -> {
            // Same jurisdiction (DEMO-US -> DEMO-US): the 6% state and 2% federal components apply, the 10% cross-jurisdiction one does not.
            Calculation august = calculate(context, snapshotFromSeed(0, fixture));
            assertThat(august.saved().getTaxableAmount()).isEqualByComparingTo("2137.50");
            assertThat(august.saved().getTotalTaxAmount()).isEqualByComparingTo("171.00");
            assertThat(august.saved().getGrandTotal()).isEqualByComparingTo("2308.50");
            assertThat(august.saved().getComponents()).extracting(TaxCalculationComponent::getTaxTypeCode)
                    .containsExactlyInAnyOrder("DEMO-STATE", "DEMO-FEDERAL");
            assertThat(august.snapshot().getStatus()).isEqualTo(BillingSnapshotStatus.TAX_COMPLETED);

            Calculation september = calculate(context, snapshotFromSeed(1, fixture));
            assertThat(september.saved().getTaxableAmount()).isEqualByComparingTo("1342.50");
            assertThat(september.saved().getTotalTaxAmount()).isEqualByComparingTo("107.40");
            assertThat(september.saved().getGrandTotal()).isEqualByComparingTo("1449.90");
            assertThat(september.saved().getComponents()).extracting(TaxCalculationComponent::getAppliedRate)
                    .map(BigDecimal::stripTrailingZeros).containsExactlyInAnyOrder(new BigDecimal("6"), new BigDecimal("2"));
        });
    }

    @Test
    void theDocumentedTotalsInTheSeedTrailerMatchTheRealCalculation() {
        withSeededTax((fixture, context) -> {
            String trailer = SeedSql.read().substring(SeedSql.read().indexOf("-- Done."));
            Calculation august = calculate(context, snapshotFromSeed(0, fixture));
            Calculation september = calculate(context, snapshotFromSeed(1, fixture));

            assertThat(trailer).contains("total tax " + august.saved().getTotalTaxAmount().toPlainString())
                    .contains("grand total " + august.saved().getGrandTotal().toPlainString())
                    .contains("total tax " + september.saved().getTotalTaxAmount().toPlainString())
                    .contains("grand total " + september.saved().getGrandTotal().toPlainString());
        });
    }

    @Test
    void aDifferentJurisdictionAppliesOnlyTheCrossJurisdictionComponent() {
        withSeededTax((fixture, context) -> {
            BillingSnapshot snapshot = snapshotFromSeed(0, fixture);
            snapshot.setDestinationTaxJurisdictionCode("DEMO-IN");

            Calculation calculation = calculate(context, snapshot);

            assertThat(calculation.saved().getComponents()).extracting(TaxCalculationComponent::getTaxTypeCode).containsExactly("DEMO-CROSS");
            assertThat(calculation.saved().getTotalTaxAmount()).isEqualByComparingTo("213.75"); // 10% of 2137.50
        });
    }

    @Test
    void theOldSeedShapeWouldHaveFailedInTheRealService() {
        withSeededTax((fixture, context) -> {
            BillingSnapshot noRegion = snapshotFromSeed(0, fixture);
            noRegion.setTaxRegionId(null); // what the previous seed contained
            assertThatThrownBy(() -> calculate(context, noRegion))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                    .hasMessageContaining("Tax region is not configured for this billing snapshot.");

            BillingSnapshot alreadyDone = snapshotFromSeed(0, fixture);
            alreadyDone.setStatus(BillingSnapshotStatus.TAX_COMPLETED); // a snapshot claiming tax is done without a calculation
            assertThatThrownBy(() -> calculate(context, alreadyDone))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                    .hasMessageContaining("not ready for tax calculation");
        });
    }

    @Test
    void anUnconfiguredRegionOrPeriodIsRejectedWithTheRealMessage() {
        withSeededTax((fixture, context) -> {
            BillingSnapshot beforeConfiguration = snapshotFromSeed(0, fixture);
            beforeConfiguration.setBillingPeriodStart(LocalDate.of(2025, 12, 1));
            assertThatThrownBy(() -> calculate(context, beforeConfiguration))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                    .hasMessageContaining("No active tax configuration is available for the selected tax region and billing period.");
        });
    }

    @SuppressWarnings("unused")
    private static <T> Consumer<T> unused() {
        return t -> {
        };
    }
}
