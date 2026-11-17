package com.AccountReceivableManagement.service_Imple.automatic_billing;

import com.AccountReceivableManagement.entity.automatic_billing.AutomaticBillingRun;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingStep;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingScheduleType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType;
import com.AccountReceivableManagement.global_exception_handler.AutomaticBillingRunClaimLostException;
import com.AccountReceivableManagement.repo.automatic_billing.AutomaticBillingRunRepository;
import com.AccountReceivableManagement.repo.client.ClientRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTypeMasterRepository;
import com.AccountReceivableManagement.service_Imple.projectbilling_config.BillingOccurrenceServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Database-level behaviour of AutomaticBillingRun against a real (in-memory
 * H2, MySQL mode) database: unique keys, idempotent creation, and the atomic
 * claim under real concurrency. Not wrapped in a test transaction - the
 * service commits each state change itself, as it will in production.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:automatic_billing_run;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AutomaticBillingRunPersistenceTest {

    /** 01-Nov-2026 10:00 - the October occurrences below are due. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneId.of("UTC"));

    @Autowired private AutomaticBillingRunRepository runRepository;
    @Autowired private BillingScheduleRepository scheduleRepository;
    @Autowired private BillingConfigurationRepository configurationRepository;
    @Autowired private BillingTypeMasterRepository billingTypeRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private AutomaticBillingRunServiceImpl service;
    private TransactionTemplate tx;
    private BillingConfiguration configuration;
    private BillingSchedule october;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        service = new AutomaticBillingRunServiceImpl(runRepository, scheduleRepository, configurationRepository,
                mock(BillingOccurrenceServiceImpl.class), transactionManager, CLOCK);

        configuration = tx.execute(status -> {
            Client client = clientRepository.save(Client.builder()
                    .clientId(UUID.randomUUID()).clientName("Acme").build());
            BillingTypeMaster fixedPrice = billingTypeRepository.save(BillingTypeMaster.builder()
                    .billingTypeName("Fixed Price " + UUID.randomUUID()).isActive(true).build());
            return configurationRepository.save(BillingConfiguration.builder()
                    .client(client)
                    .billingType(fixedPrice)
                    .approvalStatus(ApprovalStatus.APPROVED)
                    .invoiceGenerationType(InvoiceGenerationType.AUTOMATIC)
                    .build());
        });
        october = saveOccurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(status -> {
            runRepository.deleteAll();
            scheduleRepository.deleteAll();
            configurationRepository.deleteAll();
            billingTypeRepository.deleteAll();
            clientRepository.deleteAll();
        });
    }

    private BillingSchedule saveOccurrence(LocalDate start, LocalDate end) {
        return tx.execute(status -> scheduleRepository.save(BillingSchedule.builder()
                .billingConfiguration(configuration)
                .periodNumber(start.getMonthValue())
                .periodStartDate(start)
                .periodEndDate(end)
                .billingDate(end)
                .billingAmount(new BigDecimal("100000.00"))
                .scheduleType(BillingScheduleType.PRIMARY)
                .periodStatus(BillingPeriodStatus.TAX_PENDING)
                .taxStatus(BillingPeriodStatus.TAX_PENDING)
                .isInvoiced(false)
                .isActive(true)
                .build()));
    }

    private <T> List<T> runConcurrently(int workers, Callable<T> work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return work.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    // ================================================================ creation & relationships

    @Test
    void createdRun_hasDefaultsRelationshipsAndTimestamps() {
        AutomaticBillingRun run = service.findOrCreateForOccurrence(october.getBillingScheduleId());

        AutomaticBillingRun stored = tx.execute(status -> {
            AutomaticBillingRun loaded = runRepository.findById(run.getAutomaticBillingRunId()).orElseThrow();
            loaded.getBillingConfiguration().getBillingConfigurationId();
            return loaded;
        });

        assertThat(stored.getStatus()).isEqualTo(AutomaticBillingRunStatus.SCHEDULED);
        assertThat(stored.getAttemptCount()).isZero();
        assertThat(stored.getVersion()).isNotNull();
        assertThat(stored.getBillingConfiguration().getBillingConfigurationId())
                .isEqualTo(configuration.getBillingConfigurationId());
        assertThat(stored.getBillingScheduleId()).isEqualTo(october.getBillingScheduleId());
        assertThat(stored.getBillingPeriodStart()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(stored.getBillingPeriodEnd()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(stored.isOccurrenceBased()).isTrue();
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isNotNull();
        assertThat(stored.getStartedAt()).isNull();
        assertThat(stored.getCurrentStep()).isNull();
    }

    @Test
    void repository_findsRunByOccurrenceAndByBusinessKey() {
        AutomaticBillingRun run = service.findOrCreateForOccurrence(october.getBillingScheduleId());

        assertThat(runRepository.findByBillingScheduleId(october.getBillingScheduleId()))
                .map(AutomaticBillingRun::getAutomaticBillingRunId)
                .contains(run.getAutomaticBillingRunId());
        assertThat(runRepository
                .findByBillingConfiguration_BillingConfigurationIdAndBillingPeriodStartAndBillingPeriodEnd(
                        configuration.getBillingConfigurationId(), LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 31)))
                .map(AutomaticBillingRun::getAutomaticBillingRunId)
                .contains(run.getAutomaticBillingRunId());
        assertThat(runRepository.findByBillingScheduleId(UUID.randomUUID())).isEmpty();
    }

    // ================================================================ idempotency

    @Test
    void repeatedCreation_returnsTheSameRun() {
        AutomaticBillingRun first = service.findOrCreateForOccurrence(october.getBillingScheduleId());
        AutomaticBillingRun second = service.findOrCreateForOccurrence(october.getBillingScheduleId());

        assertThat(second.getAutomaticBillingRunId()).isEqualTo(first.getAutomaticBillingRunId());
        assertThat(runRepository.count()).isEqualTo(1);
    }

    @Test
    void databaseRejectsASecondRunForTheSameBillingEvent() {
        service.findOrCreateForOccurrence(october.getBillingScheduleId());

        assertThatThrownBy(() -> tx.execute(status -> runRepository.saveAndFlush(AutomaticBillingRun.builder()
                .billingConfiguration(configuration)
                .billingPeriodStart(LocalDate.of(2026, 10, 1))
                .billingPeriodEnd(LocalDate.of(2026, 10, 31))
                .build())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(runRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentCreationForTheSameOccurrence_yieldsOneRun() throws Exception {
        List<UUID> runIds = runConcurrently(8, () ->
                service.findOrCreateForOccurrence(october.getBillingScheduleId()).getAutomaticBillingRunId());

        assertThat(runIds).hasSize(8).containsOnly(runIds.get(0));
        assertThat(runRepository.count()).isEqualTo(1);
    }

    @Test
    void differentOccurrences_getIndependentRuns() {
        BillingSchedule november = saveOccurrence(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 30));

        AutomaticBillingRun octoberRun = service.findOrCreateForOccurrence(october.getBillingScheduleId());
        AutomaticBillingRun novemberRun = service.findOrCreateForOccurrence(november.getBillingScheduleId());

        assertThat(novemberRun.getAutomaticBillingRunId()).isNotEqualTo(octoberRun.getAutomaticBillingRunId());
        assertThat(runRepository.count()).isEqualTo(2);
    }

    // ================================================================ claiming

    @Test
    void claim_startsAnAttempt() {
        UUID runId = service.findOrCreateForOccurrence(october.getBillingScheduleId()).getAutomaticBillingRunId();

        Optional<UUID> token = service.claim(runId, Duration.ofMinutes(10));

        AutomaticBillingRun run = runRepository.findById(runId).orElseThrow();
        assertThat(token).isPresent();
        assertThat(run.getStatus()).isEqualTo(AutomaticBillingRunStatus.PROCESSING);
        assertThat(run.getAttemptCount()).isEqualTo(1);
        assertThat(run.getClaimToken()).isEqualTo(token.get());
        assertThat(run.getStartedAt()).isEqualTo(java.time.LocalDateTime.now(CLOCK));
        assertThat(run.getClaimExpiresAt()).isEqualTo(java.time.LocalDateTime.now(CLOCK).plusMinutes(10));
    }

    @Test
    void readingARun_doesNotCountAsAnAttempt() {
        UUID runId = service.findOrCreateForOccurrence(october.getBillingScheduleId()).getAutomaticBillingRunId();

        service.findById(runId);
        service.findOrCreateForOccurrence(october.getBillingScheduleId());

        assertThat(runRepository.findById(runId).orElseThrow().getAttemptCount()).isZero();
    }

    @Test
    void concurrentClaims_exactlyOneWorkerOwnsTheRun() throws Exception {
        UUID runId = service.findOrCreateForOccurrence(october.getBillingScheduleId()).getAutomaticBillingRunId();

        List<Optional<UUID>> claims = runConcurrently(8, () -> service.claim(runId, Duration.ofMinutes(10)));

        assertThat(claims.stream().filter(Optional::isPresent)).hasSize(1);
        AutomaticBillingRun run = runRepository.findById(runId).orElseThrow();
        assertThat(run.getAttemptCount()).isEqualTo(1);
        assertThat(run.getClaimToken()).isEqualTo(claims.stream().flatMap(Optional::stream).findFirst().orElseThrow());
    }

    @Test
    void processingRun_cannotBeClaimedWhileItsClaimIsValid() {
        UUID runId = service.findOrCreateForOccurrence(october.getBillingScheduleId()).getAutomaticBillingRunId();
        service.claim(runId, Duration.ofMinutes(10));

        assertThat(service.claim(runId, Duration.ofMinutes(10))).isEmpty();
    }

    /** A worker that died: once its claim expires another worker takes over, and the old token is dead. */
    @Test
    void expiredClaim_isTakenOverAndTheStaleWorkerLosesTheRun() {
        UUID runId = service.findOrCreateForOccurrence(october.getBillingScheduleId()).getAutomaticBillingRunId();
        UUID staleToken = service.claim(runId, Duration.ofMinutes(10)).orElseThrow();

        AutomaticBillingRunServiceImpl later = new AutomaticBillingRunServiceImpl(runRepository, scheduleRepository,
                configurationRepository, mock(BillingOccurrenceServiceImpl.class), transactionManager,
                Clock.offset(CLOCK, Duration.ofMinutes(11)));
        UUID newToken = later.claim(runId, Duration.ofMinutes(10)).orElseThrow();

        assertThat(newToken).isNotEqualTo(staleToken);
        assertThat(runRepository.findById(runId).orElseThrow().getAttemptCount()).isEqualTo(2);
        assertThatThrownBy(() -> later.startStep(runId, staleToken, AutomaticBillingStep.TAX_CALCULATION))
                .isInstanceOf(AutomaticBillingRunClaimLostException.class);
        assertThat(later.startStep(runId, newToken, AutomaticBillingStep.TAX_CALCULATION).getCurrentStep())
                .isEqualTo(AutomaticBillingStep.TAX_CALCULATION);
    }

    @Test
    void failedRun_canBeClaimedAgain_onlyOnceItsRetryTimeHasPassed() {
        UUID runId = service.findOrCreateForOccurrence(october.getBillingScheduleId()).getAutomaticBillingRunId();
        UUID token = service.claim(runId, Duration.ofMinutes(10)).orElseThrow();
        service.startStep(runId, token, AutomaticBillingStep.TAX_CALCULATION);
        service.markFailed(runId, token, "Tax region missing", java.time.LocalDateTime.now(CLOCK).plusHours(1));

        assertThat(service.claim(runId, Duration.ofMinutes(10))).isEmpty();

        AutomaticBillingRunServiceImpl inTwoHours = new AutomaticBillingRunServiceImpl(runRepository,
                scheduleRepository, configurationRepository, mock(BillingOccurrenceServiceImpl.class),
                transactionManager, Clock.offset(CLOCK, Duration.ofHours(2)));
        assertThat(inTwoHours.claim(runId, Duration.ofMinutes(10))).isPresent();

        AutomaticBillingRun run = runRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(AutomaticBillingRunStatus.PROCESSING);
        assertThat(run.getAttemptCount()).isEqualTo(2);
        // The last failure stays visible after recovery.
        assertThat(run.getFailureStep()).isEqualTo(AutomaticBillingStep.TAX_CALCULATION);
    }

    @Test
    void completedRun_cannotBeClaimed() {
        UUID runId = service.findOrCreateForOccurrence(october.getBillingScheduleId()).getAutomaticBillingRunId();
        UUID token = service.claim(runId, Duration.ofMinutes(10)).orElseThrow();
        service.startStep(runId, token, AutomaticBillingStep.INVOICE_CREATION);
        service.completeStep(runId, token, AutomaticBillingStep.INVOICE_CREATION, null, UUID.randomUUID());
        service.markCompleted(runId, token);

        assertThat(service.claim(runId, Duration.ofMinutes(10))).isEmpty();
        assertThat(runRepository.findById(runId).orElseThrow().getStatus())
                .isEqualTo(AutomaticBillingRunStatus.COMPLETED);
    }

    // ================================================================ version

    @Test
    void staleEntityCopy_isRejectedByOptimisticLocking() {
        UUID runId = service.findOrCreateForOccurrence(october.getBillingScheduleId()).getAutomaticBillingRunId();
        AutomaticBillingRun staleCopy = runRepository.findById(runId).orElseThrow();
        service.claim(runId, Duration.ofMinutes(10));

        staleCopy.setFailureReason("overwritten");
        assertThatThrownBy(() -> tx.execute(status -> runRepository.saveAndFlush(staleCopy)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // ================================================================ full lifecycle

    @Test
    void lifecycle_recordsStepsReferencesAndEndsPendingApproval() {
        UUID runId = service.findOrCreateForOccurrence(october.getBillingScheduleId()).getAutomaticBillingRunId();
        UUID token = service.claim(runId, Duration.ofMinutes(10)).orElseThrow();
        UUID invoiceId = UUID.randomUUID();

        for (AutomaticBillingStep step : AutomaticBillingStep.values()) {
            service.startStep(runId, token, step);
            service.completeStep(runId, token, step, null,
                    step == AutomaticBillingStep.INVOICE_CREATION ? invoiceId : null);
        }
        service.markPendingApproval(runId, token);

        AutomaticBillingRun run = runRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        assertThat(run.getLastCompletedStep()).isEqualTo(AutomaticBillingStep.SUBMIT_FOR_APPROVAL);
        assertThat(run.getInvoiceId()).isEqualTo(invoiceId);
        assertThat(run.getClaimToken()).isNull();

        service.completeAfterApproval(runId);
        AutomaticBillingRun completed = runRepository.findById(runId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(AutomaticBillingRunStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
    }
}
