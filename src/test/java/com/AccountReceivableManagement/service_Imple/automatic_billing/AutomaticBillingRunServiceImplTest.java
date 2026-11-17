package com.AccountReceivableManagement.service_Imple.automatic_billing;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingPeriodDto;
import com.AccountReceivableManagement.entity.automatic_billing.AutomaticBillingRun;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingStep;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType;
import com.AccountReceivableManagement.global_exception_handler.AutomaticBillingRunClaimLostException;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.automatic_billing.AutomaticBillingRunRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.service_Imple.projectbilling_config.BillingOccurrenceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomaticBillingRunServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneId.of("UTC"));

    @Mock private AutomaticBillingRunRepository runRepository;
    @Mock private BillingScheduleRepository scheduleRepository;
    @Mock private BillingConfigurationRepository configurationRepository;
    @Mock private BillingOccurrenceServiceImpl billingOccurrenceService;
    @Mock private PlatformTransactionManager transactionManager;

    private AutomaticBillingRunServiceImpl service;

    private final UUID runId = UUID.randomUUID();
    private final UUID token = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AutomaticBillingRunServiceImpl(runRepository, scheduleRepository, configurationRepository,
                billingOccurrenceService, transactionManager, CLOCK);
        lenient().when(runRepository.saveAndFlush(any(AutomaticBillingRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private AutomaticBillingRun processingRun() {
        return AutomaticBillingRun.builder()
                .automaticBillingRunId(runId)
                .status(AutomaticBillingRunStatus.PROCESSING)
                .claimToken(token)
                .claimExpiresAt(LocalDateTime.now(CLOCK).plusMinutes(10))
                .attemptCount(1)
                .billingPeriodStart(LocalDate.of(2026, 10, 1))
                .billingPeriodEnd(LocalDate.of(2026, 10, 31))
                .build();
    }

    private AutomaticBillingRun stubLocked(AutomaticBillingRun run) {
        when(runRepository.findByIdForUpdate(runId)).thenReturn(Optional.of(run));
        return run;
    }

    // ================================================================ state model

    @Nested
    class StateModel {

        @Test
        void validTransitions() {
            assertThat(AutomaticBillingRunStatus.SCHEDULED.canTransitionTo(AutomaticBillingRunStatus.PROCESSING)).isTrue();
            assertThat(AutomaticBillingRunStatus.PROCESSING.canTransitionTo(AutomaticBillingRunStatus.FAILED)).isTrue();
            assertThat(AutomaticBillingRunStatus.PROCESSING.canTransitionTo(AutomaticBillingRunStatus.PENDING_APPROVAL)).isTrue();
            assertThat(AutomaticBillingRunStatus.PROCESSING.canTransitionTo(AutomaticBillingRunStatus.COMPLETED)).isTrue();
            assertThat(AutomaticBillingRunStatus.FAILED.canTransitionTo(AutomaticBillingRunStatus.PROCESSING)).isTrue();
            assertThat(AutomaticBillingRunStatus.PENDING_APPROVAL.canTransitionTo(AutomaticBillingRunStatus.COMPLETED)).isTrue();
        }

        @ParameterizedTest
        @EnumSource(AutomaticBillingRunStatus.class)
        void completedIsTerminal(AutomaticBillingRunStatus target) {
            assertThat(AutomaticBillingRunStatus.COMPLETED.canTransitionTo(target)).isFalse();
        }

        @Test
        void invalidTransitions() {
            assertThat(AutomaticBillingRunStatus.SCHEDULED.canTransitionTo(AutomaticBillingRunStatus.COMPLETED)).isFalse();
            assertThat(AutomaticBillingRunStatus.SCHEDULED.canTransitionTo(AutomaticBillingRunStatus.FAILED)).isFalse();
            assertThat(AutomaticBillingRunStatus.FAILED.canTransitionTo(AutomaticBillingRunStatus.COMPLETED)).isFalse();
            assertThat(AutomaticBillingRunStatus.PENDING_APPROVAL.canTransitionTo(AutomaticBillingRunStatus.PROCESSING)).isFalse();
        }

        @Test
        void completeAfterApproval_fromAnythingButPendingApproval_isRejected() {
            AutomaticBillingRun run = stubLocked(processingRun());
            run.setStatus(AutomaticBillingRunStatus.SCHEDULED);

            assertThatThrownBy(() -> service.completeAfterApproval(runId))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                    .hasMessageContaining("cannot move from SCHEDULED to COMPLETED");
            verify(runRepository, never()).saveAndFlush(any());
        }
    }

    // ================================================================ steps

    @Nested
    class Steps {

        @Test
        void startAndCompleteStep_recordsProgressAndReferences() {
            AutomaticBillingRun run = stubLocked(processingRun());
            UUID snapshotId = UUID.randomUUID();

            service.startStep(runId, token, AutomaticBillingStep.SOURCE_DATA_ACQUISITION);
            assertThat(run.getCurrentStep()).isEqualTo(AutomaticBillingStep.SOURCE_DATA_ACQUISITION);

            service.completeStep(runId, token, AutomaticBillingStep.SOURCE_DATA_ACQUISITION, snapshotId, null);
            assertThat(run.getCurrentStep()).isNull();
            assertThat(run.getLastCompletedStep()).isEqualTo(AutomaticBillingStep.SOURCE_DATA_ACQUISITION);
            assertThat(run.getBillingSnapshotId()).isEqualTo(snapshotId);
        }

        @Test
        void aCompletedStepCannotBeStartedAgain() {
            AutomaticBillingRun run = stubLocked(processingRun());
            run.setLastCompletedStep(AutomaticBillingStep.TAX_CALCULATION);

            assertThatThrownBy(() -> service.startStep(runId, token, AutomaticBillingStep.SOURCE_DATA_ACQUISITION))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class);
        }

        @Test
        void onlyTheStepInProgressCanBeCompleted() {
            AutomaticBillingRun run = stubLocked(processingRun());
            run.setCurrentStep(AutomaticBillingStep.TAX_CALCULATION);

            assertThatThrownBy(() -> service.completeStep(runId, token, AutomaticBillingStep.INVOICE_CREATION,
                    null, UUID.randomUUID()))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class);
        }

        @Test
        void aRecordedInvoiceIsNeverReplacedByAnother() {
            AutomaticBillingRun run = stubLocked(processingRun());
            run.setInvoiceId(UUID.randomUUID());
            run.setCurrentStep(AutomaticBillingStep.INVOICE_CREATION);

            assertThatThrownBy(() -> service.completeStep(runId, token, AutomaticBillingStep.INVOICE_CREATION,
                    null, UUID.randomUUID()))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                    .hasMessageContaining("cannot be replaced");
        }

        @Test
        void pendingApproval_requiresTheSubmittedInvoice() {
            stubLocked(processingRun());

            assertThatThrownBy(() -> service.markPendingApproval(runId, token))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class);
        }

        @Test
        void completion_requiresAnInvoice() {
            stubLocked(processingRun());

            assertThatThrownBy(() -> service.markCompleted(runId, token))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class);
        }
    }

    // ================================================================ ownership

    @Nested
    class Ownership {

        @Test
        void wrongClaimToken_isRejectedAndNothingChanges() {
            AutomaticBillingRun run = stubLocked(processingRun());

            assertThatThrownBy(() -> service.startStep(runId, UUID.randomUUID(), AutomaticBillingStep.TAX_CALCULATION))
                    .isInstanceOf(AutomaticBillingRunClaimLostException.class);
            assertThat(run.getCurrentStep()).isNull();
            verify(runRepository, never()).saveAndFlush(any());
        }

        @Test
        void runNoLongerProcessing_isRejected() {
            AutomaticBillingRun run = stubLocked(processingRun());
            run.setStatus(AutomaticBillingRunStatus.FAILED);

            assertThatThrownBy(() -> service.markFailed(runId, token, "again", null))
                    .isInstanceOf(AutomaticBillingRunClaimLostException.class);
        }

        @Test
        void noClaimToken_isRejected() {
            stubLocked(processingRun());

            assertThatThrownBy(() -> service.startStep(runId, null, AutomaticBillingStep.TAX_CALCULATION))
                    .isInstanceOf(AutomaticBillingRunClaimLostException.class);
        }
    }

    // ================================================================ failure

    @Nested
    class Failure {

        @Test
        void failure_recordsStepReasonTimeAndReleasesTheClaim() {
            AutomaticBillingRun run = stubLocked(processingRun());
            run.setCurrentStep(AutomaticBillingStep.SOURCE_DATA_ACQUISITION);
            LocalDateTime retryAt = LocalDateTime.now(CLOCK).plusHours(1);

            service.markFailed(runId, token, "TMS service failed with status 503.", retryAt);

            assertThat(run.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
            assertThat(run.getFailureStep()).isEqualTo(AutomaticBillingStep.SOURCE_DATA_ACQUISITION);
            assertThat(run.getCurrentStep()).isEqualTo(AutomaticBillingStep.SOURCE_DATA_ACQUISITION);
            assertThat(run.getFailureReason()).isEqualTo("TMS service failed with status 503.");
            assertThat(run.getFailedAt()).isEqualTo(LocalDateTime.now(CLOCK));
            assertThat(run.getNextRetryAt()).isEqualTo(retryAt);
            assertThat(run.getClaimToken()).isNull();
            assertThat(run.getClaimExpiresAt()).isNull();
        }

        @Test
        void bearerTokensAndJwtsAreNeverPersisted() {
            String jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZGl0eWEifQ.c2lnbmF0dXJlLXBhcnQ";
            String sanitized = AutomaticBillingRunServiceImpl.sanitizeFailureReason(
                    "TMS 401 for header Authorization: Bearer " + jwt + " (token " + jwt + ")");

            assertThat(sanitized).doesNotContain(jwt).doesNotContain("eyJ");
            assertThat(sanitized).contains("[REDACTED]");
        }

        @Test
        void passwordsSecretsAndUrlCredentialsAreNeverPersisted() {
            String sanitized = AutomaticBillingRunServiceImpl.sanitizeFailureReason(
                    "Cannot connect jdbc:mysql://host:3306/ar?user=avnadmin&password=AVNS_s3cr3t; "
                            + "client_secret=abc123 api-key: xyz789 https://svc:hunter2@tms.local/api");

            assertThat(sanitized)
                    .doesNotContain("AVNS_s3cr3t")
                    .doesNotContain("abc123")
                    .doesNotContain("xyz789")
                    .doesNotContain("hunter2");
            assertThat(sanitized).contains("password=[REDACTED]");
        }

        @Test
        void ordinaryBusinessMessagesAreKept() {
            assertThat(AutomaticBillingRunServiceImpl.sanitizeFailureReason(
                    "No active tax configuration is available for the selected tax region and billing period."))
                    .isEqualTo("No active tax configuration is available for the selected tax region and billing period.");
        }

        @Test
        void longOrEmptyReasons_fitTheColumn() {
            assertThat(AutomaticBillingRunServiceImpl.sanitizeFailureReason("x".repeat(5000)))
                    .hasSize(AutomaticBillingRun.FAILURE_REASON_MAX_LENGTH);
            assertThat(AutomaticBillingRunServiceImpl.sanitizeFailureReason(null)).isEqualTo("Unknown failure.");
            assertThat(AutomaticBillingRunServiceImpl.sanitizeFailureReason("  ")).isEqualTo("Unknown failure.");
        }

        @Test
        void storedFailureReasonIsSanitized() {
            AutomaticBillingRun run = stubLocked(processingRun());

            service.markFailed(runId, token, "401: Bearer abc.def.ghi rejected", null);

            assertThat(run.getFailureReason()).doesNotContain("abc.def.ghi").contains("Bearer [REDACTED]");
        }
    }

    // ================================================================ creation eligibility

    @Nested
    class Creation {

        private BillingConfiguration configuration(ApprovalStatus approval, InvoiceGenerationType generation) {
            return BillingConfiguration.builder()
                    .billingConfigurationId(UUID.randomUUID())
                    .approvalStatus(approval)
                    .invoiceGenerationType(generation)
                    .build();
        }

        private BillingSchedule occurrence(BillingConfiguration configuration, LocalDate billingDate) {
            return BillingSchedule.builder()
                    .billingScheduleId(UUID.randomUUID())
                    .billingConfiguration(configuration)
                    .periodStartDate(billingDate.withDayOfMonth(1))
                    .periodEndDate(billingDate)
                    .billingDate(billingDate)
                    .periodStatus(BillingPeriodStatus.SCHEDULED)
                    .isInvoiced(false)
                    .isActive(true)
                    .build();
        }

        @Test
        void manualConfiguration_getsNoRun() {
            BillingSchedule occurrence = occurrence(
                    configuration(ApprovalStatus.APPROVED, InvoiceGenerationType.MANUAL), LocalDate.of(2026, 10, 31));
            when(scheduleRepository.findById(occurrence.getBillingScheduleId())).thenReturn(Optional.of(occurrence));

            assertThatThrownBy(() -> service.findOrCreateForOccurrence(occurrence.getBillingScheduleId()))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                    .hasMessageContaining("AUTOMATIC");
            verify(runRepository, never()).saveAndFlush(any());
        }

        @Test
        void unapprovedConfiguration_getsNoRun() {
            BillingSchedule occurrence = occurrence(
                    configuration(ApprovalStatus.PENDING_APPROVAL, InvoiceGenerationType.AUTOMATIC),
                    LocalDate.of(2026, 10, 31));
            when(scheduleRepository.findById(occurrence.getBillingScheduleId())).thenReturn(Optional.of(occurrence));

            assertThatThrownBy(() -> service.findOrCreateForOccurrence(occurrence.getBillingScheduleId()))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class);
        }

        @Test
        void occurrenceNotYetDue_getsNoRun() {
            BillingSchedule occurrence = occurrence(
                    configuration(ApprovalStatus.APPROVED, InvoiceGenerationType.AUTOMATIC),
                    LocalDate.of(2026, 11, 30));
            when(scheduleRepository.findById(occurrence.getBillingScheduleId())).thenReturn(Optional.of(occurrence));

            assertThatThrownBy(() -> service.findOrCreateForOccurrence(occurrence.getBillingScheduleId()))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                    .hasMessageContaining("not due");
        }

        @Test
        void existingRun_isReturnedWithoutValidatingOrInserting() {
            UUID scheduleId = UUID.randomUUID();
            AutomaticBillingRun existing = processingRun();
            when(runRepository.findByBillingScheduleId(scheduleId)).thenReturn(Optional.of(existing));

            assertThat(service.findOrCreateForOccurrence(scheduleId)).isSameAs(existing);
            verify(runRepository, never()).saveAndFlush(any());
        }

        @Test
        void timeAndMaterial_periodMustBeOneOfTheConfiguredBillingPeriods() {
            BillingConfiguration configuration =
                    configuration(ApprovalStatus.APPROVED, InvoiceGenerationType.AUTOMATIC);
            UUID configId = configuration.getBillingConfigurationId();
            when(configurationRepository.findById(configId)).thenReturn(Optional.of(configuration));
            when(billingOccurrenceService.resolveTimeAndMaterialBillingPeriods(configId, LocalDate.now(CLOCK)))
                    .thenReturn(List.of(BillingPeriodDto.builder()
                            .periodStartDate(LocalDate.of(2026, 10, 15))
                            .periodEndDate(LocalDate.of(2026, 11, 14))
                            .billingDate(LocalDate.of(2026, 11, 14))
                            .build()));

            // A calendar month is not this configuration's period.
            assertThatThrownBy(() -> service.findOrCreateForTimeAndMaterialPeriod(
                    configId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                    .hasMessageContaining("not one of this configuration's billing periods");
            // Its own period is, but is not due until 14-Nov.
            assertThatThrownBy(() -> service.findOrCreateForTimeAndMaterialPeriod(
                    configId, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 14)))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                    .hasMessageContaining("not due until 2026-11-14");
            verify(runRepository, never()).saveAndFlush(any());
        }

        @Test
        void timeAndMaterial_duePeriod_getsARunWithoutAnOccurrence() {
            BillingConfiguration configuration =
                    configuration(ApprovalStatus.APPROVED, InvoiceGenerationType.AUTOMATIC);
            UUID configId = configuration.getBillingConfigurationId();
            when(configurationRepository.findById(configId)).thenReturn(Optional.of(configuration));
            when(billingOccurrenceService.resolveTimeAndMaterialBillingPeriods(configId, LocalDate.now(CLOCK)))
                    .thenReturn(List.of(BillingPeriodDto.builder()
                            .periodStartDate(LocalDate.of(2026, 10, 1))
                            .periodEndDate(LocalDate.of(2026, 10, 31))
                            .billingDate(LocalDate.of(2026, 10, 31))
                            .build()));

            AutomaticBillingRun run = service.findOrCreateForTimeAndMaterialPeriod(
                    configId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

            assertThat(run.getBillingScheduleId()).isNull();
            assertThat(run.isOccurrenceBased()).isFalse();
            assertThat(run.getBillingConfiguration()).isSameAs(configuration);
            assertThat(run.getStatus()).isEqualTo(AutomaticBillingRunStatus.SCHEDULED);
        }
    }
}
