package com.AccountReceivableManagement.integration_mysql;

import com.AccountReceivableManagement.entity.automatic_billing.AutomaticBillingRun;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType;
import com.AccountReceivableManagement.repo.automatic_billing.AutomaticBillingRunRepository;
import com.AccountReceivableManagement.repo.client.ClientRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTypeMasterRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C3 - the database behaviour that matters for financial correctness, on the
 * MySQL version production runs (8.0), not H2: the schema Hibernate maps,
 * the unique keys that prevent duplicate billing effects, the V12-V14
 * migrations applied by Flyway to a V11 schema, and the atomic run claim.
 * <p>
 * Runs a throwaway MySQL container; skipped when Docker is not available
 * (it is never pointed at a shared database). Run with Docker running:
 * {@code mvnw test -Dtest=MySqlIntegrationTest}.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MySqlIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42")
            .withDatabaseName("accounts_receivable_it");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private AutomaticBillingRunRepository runRepository;
    @Autowired private BillingConfigurationRepository configurationRepository;
    @Autowired private BillingTypeMasterRepository billingTypeRepository;
    @Autowired private ClientRepository clientRepository;

    // ---------------------------------------------------------------- unique keys

    /** The keys that make duplicate invoices, runs, documents and receivables impossible. */
    @Test
    void financialUniqueKeys_existOnMySql() {
        List<String[]> expected = List.of(
                new String[]{"receivable", "uk_receivable_invoice"},
                new String[]{"invoice", "uk_invoice_number"},
                new String[]{"invoice", "uk_invoice_billing_snapshot"},
                new String[]{"invoice", "uk_invoice_billing_schedule"},
                new String[]{"invoice_document", "uk_invoice_document_invoice"},
                new String[]{"automatic_billing_run", "uk_automatic_billing_run_period"},
                new String[]{"automatic_billing_run", "uk_automatic_billing_run_schedule"},
                new String[]{"billing_snapshot_item", "uk_billing_snapshot_item_billed_source"},
                new String[]{"billing_schedule", "uk_billing_schedule_period"});
        for (String[] key : expected) {
            Integer unique = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() "
                            + "AND table_name = ? AND index_name = ? AND non_unique = 0",
                    Integer.class, key[0], key[1]);
            assertThat(unique).as(key[0] + "." + key[1]).isPositive();
        }
    }

    // ---------------------------------------------------------------- migrations

    /**
     * Upgrade path: a database at V11 (simulated by removing what V12-V14 add
     * from the mapped schema) is baselined at 11 and migrated by Flyway; the
     * result has every column and table the entities need, with the expected
     * types. Data already present survives.
     */
    @Test
    void migrationsV12toV14_upgradeAV11Schema_withoutLosingData() {
        jdbc.execute("ALTER TABLE tax_calculation DROP COLUMN currency_code");
        jdbc.execute("ALTER TABLE automatic_billing_run DROP COLUMN requeued_at");
        jdbc.execute("DROP TABLE automatic_billing_run_event");
        jdbc.execute("ALTER TABLE invoice_delivery DROP COLUMN requested_by, DROP COLUMN requested_by_user_id, "
                + "DROP COLUMN resolved_at, DROP COLUMN resolved_by, DROP COLUMN resolved_by_user_id, "
                + "DROP COLUMN resolution_reason");
        UUID runId = savedRun();

        var result = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("11")
                .target("14")
                .load()
                .migrate();

        assertThat(result.migrationsExecuted).isEqualTo(3);
        assertColumn("tax_calculation", "currency_code", "varchar");
        assertColumn("automatic_billing_run", "requeued_at", "datetime");
        assertColumn("automatic_billing_run_event", "run_id", "binary");
        assertColumn("automatic_billing_run_event", "failure_reason", "varchar");
        assertColumn("invoice_delivery", "resolution_reason", "varchar");
        assertColumn("invoice_delivery", "requested_by_user_id", "bigint");
        assertThat(runRepository.findById(runId)).isPresent();

        // History is consistent: validate passes, nothing pending.
        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .baselineVersion("11").target("14").load();
        flyway.validate();
        assertThat(flyway.info().pending()).isEmpty();
    }

    private void assertColumn(String table, String column, String dataType) {
        String actual = jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name = ? AND column_name = ?", String.class, table, column);
        assertThat(actual).as(table + "." + column).isEqualToIgnoringCase(dataType);
    }

    // ---------------------------------------------------------------- run claim

    /** The atomic claim on MySQL's row locks: of concurrent workers exactly one owns the run. */
    @Test
    void concurrentClaims_ofOneRun_haveExactlyOneWinner() throws Exception {
        UUID runId = savedRun();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> claims = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                claims.add(pool.submit(() -> {
                    start.await();
                    LocalDateTime now = LocalDateTime.now();
                    return tx.execute(status -> runRepository.claim(runId, UUID.randomUUID(), now.plusMinutes(15),
                            now, AutomaticBillingRunStatus.CLAIMABLE));
                }));
            }
            start.countDown();
            int won = 0;
            for (Future<Integer> claim : claims) {
                won += claim.get(60, TimeUnit.SECONDS);
            }
            assertThat(won).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(runRepository.findById(runId).orElseThrow().getAttemptCount()).isEqualTo(1);
    }

    private UUID savedRun() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            Client client = clientRepository.save(Client.builder().clientId(UUID.randomUUID())
                    .clientName("Acme").build());
            BillingTypeMaster type = billingTypeRepository.save(BillingTypeMaster.builder()
                    .billingTypeName("Fixed Price " + UUID.randomUUID()).isActive(true).build());
            BillingConfiguration configuration = configurationRepository.save(BillingConfiguration.builder()
                    .client(client).billingType(type).approvalStatus(ApprovalStatus.APPROVED)
                    .invoiceGenerationType(InvoiceGenerationType.AUTOMATIC).manuallyDeactivated(false).build());
            return runRepository.saveAndFlush(AutomaticBillingRun.builder()
                    .billingConfiguration(configuration)
                    .billingPeriodStart(LocalDate.of(2026, 10, 1))
                    .billingPeriodEnd(LocalDate.of(2026, 10, 31))
                    .build()).getAutomaticBillingRunId();
        });
    }
}
