package com.AccountReceivableManagement.endtoend;

import com.AccountReceivableManagement.entity.automatic_billing.AutomaticBillingRun;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.company_profile.CompanyProfile;
import com.AccountReceivableManagement.entity.invoice_generation.Invoice;
import com.AccountReceivableManagement.entity.invoice_generation.InvoiceApprovalHistory;
import com.AccountReceivableManagement.entity.invoice_generation.InvoiceItem;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFrequencyMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTMRateCard;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.CurrencyMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.PaymentTermsMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxConfigurationComponent;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRegionMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxTypeMaster;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingStep;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceApprovalAction;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingRatePeriod;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.PricingModel;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationUnit;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxApplicabilityType;
import com.AccountReceivableManagement.repo.automatic_billing.AutomaticBillingRunRepository;
import com.AccountReceivableManagement.repo.client.ClientRepository;
import com.AccountReceivableManagement.repo.company_profile.CompanyProfileRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceApprovalHistoryRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingFrequencyMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTMRateCardRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTypeMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.CurrencyMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.PaymentTermsMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRegionMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxTypeMasterRepository;
import com.AccountReceivableManagement.security.CurrentUserService;
import com.AccountReceivableManagement.service_Imple.automatic_billing.AutomaticBillingScheduler;
import com.AccountReceivableManagement.service_interface.email.EmailService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Automatic Time &amp; Material billing with the real service authentication
 * and the real TMS client over HTTP: the scheduler, acting as the system with
 * no user session, logs in to UMS with the service account, calls TMS with
 * that token, builds the snapshot, calculates tax, generates the invoice and
 * stops at PENDING_APPROVAL. UMS and TMS are loopback stubs; the database is
 * in-memory. Nothing here talks to a real UMS, TMS, mail server or database.
 * <p>
 * The stubs enforce the contract: UMS only issues a token for the configured
 * service account; TMS only answers a request carrying a token UMS issued.
 * Ordered, because the token provider's cache is shared by the context.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:automatic_billing_service_auth;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "client-cdc.enabled=false",
        "project-cdc.enabled=false",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/test-issuer",
        "ar.security.authorization.invoice-approval-authorities=ROLE_FINANCE_MANAGER",
        "ar.automatic-billing.enabled=true",
        // Ticked only by the test itself.
        "ar.automatic-billing.cron=0 0 0 29 2 *",
        "ar.automatic-billing.retry.initial-delay=PT1S",
        "ar.automatic-billing.retry.max-delay=PT1S",
        "tms.api.retry.initial-backoff=PT0.01S",
        "tms.api.retry.max-backoff=PT0.05S",
        "ar.ums.service-auth.enabled=true",
        "ar.ums.service-auth.email=" + AutomaticBillingServiceAuthIntegrationTest.SERVICE_EMAIL,
        "ar.ums.service-auth.password=" + AutomaticBillingServiceAuthIntegrationTest.SERVICE_PASSWORD,
        "tms.api.service-token="
})
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AutomaticBillingServiceAuthIntegrationTest {

    static final String SERVICE_EMAIL = "ar-billing-svc@test.invalid";
    static final String SERVICE_PASSWORD = "test-only-password";

    // ================================================================ UMS and TMS stubs

    private static final HttpServer STUB;
    private static final AtomicInteger UMS_LOGINS = new AtomicInteger();
    private static final List<String> ISSUED_TOKENS = new CopyOnWriteArrayList<>();
    private static final AtomicReference<String> UMS_MODE = new AtomicReference<>("ok");
    private static final AtomicReference<String> TMS_MODE = new AtomicReference<>("ok");
    private static final List<Map<String, String>> TMS_REQUESTS = new CopyOnWriteArrayList<>();

    static {
        try {
            STUB = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        STUB.createContext("/ums/auth/login", AutomaticBillingServiceAuthIntegrationTest::ums);
        STUB.createContext("/tms/api/timesheets/billing", AutomaticBillingServiceAuthIntegrationTest::tms);
        STUB.start();
    }

    @DynamicPropertySource
    static void stubEndpoints(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + STUB.getAddress().getPort();
        registry.add("ar.ums.service-auth.login-url", () -> base + "/ums/auth/login");
        registry.add("tms.api.base-url", () -> base + "/tms");
    }

    private static void ums(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        UMS_LOGINS.incrementAndGet();
        boolean serviceAccount = body.contains("\"email\":\"" + SERVICE_EMAIL + "\"")
                && body.contains("\"password\":\"" + SERVICE_PASSWORD + "\"");
        if (!serviceAccount || "reject".equals(UMS_MODE.get())) {
            respond(exchange, 401, "{\"message\":\"Invalid credentials\"}");
            return;
        }
        if ("down".equals(UMS_MODE.get())) {
            respond(exchange, 503, "");
            return;
        }
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String token = encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(("{\"sub\":\"ar-billing-svc\",\"jti\":\"" + UUID.randomUUID()
                        + "\",\"exp\":" + Instant.now().plus(Duration.ofHours(1)).getEpochSecond() + "}")
                        .getBytes(StandardCharsets.UTF_8))
                + ".signature";
        ISSUED_TOKENS.add(token);
        respond(exchange, 200, "{\"access_token\":\"" + token + "\",\"token_type\":\"Bearer\"}");
    }

    private static void tms(HttpExchange exchange) throws IOException {
        Map<String, String> query = new HashMap<>();
        for (String pair : exchange.getRequestURI().getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            query.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        query.put("authorization", String.valueOf(authorization));
        TMS_REQUESTS.add(query);

        boolean issuedToken = authorization != null && authorization.startsWith("Bearer ")
                && ISSUED_TOKENS.contains(authorization.substring(7));
        if (!issuedToken || "revoke".equals(TMS_MODE.get())) {
            respond(exchange, 401, "");
            return;
        }
        if ("down".equals(TMS_MODE.get())) {
            respond(exchange, 503, "");
            return;
        }
        LocalDate start = LocalDate.parse(query.get("billingPeriodStart"));
        String projectId = query.get("projectId");
        if ("empty".equals(TMS_MODE.get())) {
            respond(exchange, 200, "{\"projectId\":%s,\"billingPeriodStart\":\"%s\",\"billingPeriodEnd\":\"%s\",\"timesheets\":[]}"
                    .formatted(projectId, start, query.get("billingPeriodEnd")));
            return;
        }
        if ("wrongProject".equals(TMS_MODE.get())) {
            projectId = String.valueOf(Long.parseLong(projectId) + 1);
        }
        String key = projectId + "-" + start;
        respond(exchange, 200, """
                {"projectId":%s,"billingPeriodStart":"%s","billingPeriodEnd":"%s","timesheets":[
                  {"timesheetId":"TS-%s-1","resourceId":1,"resourceName":"Test Resource A","workDate":"%s",
                   "hours":8,"role":"Developer","approvalStatus":"APPROVED","billable":true},
                  {"timesheetId":"TS-%s-2","resourceId":2,"resourceName":"Test Resource B","workDate":"%s",
                   "hours":5,"role":"Senior Developer","approvalStatus":"APPROVED","billable":true}]}
                """.formatted(projectId, start, query.get("billingPeriodEnd"), key, start.plusDays(2), key,
                start.plusDays(3)));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    // ================================================================ context

    @MockBean private EmailService emailService;
    @MockBean private CurrentUserService currentUserService;

    @Autowired private AutomaticBillingScheduler scheduler;
    @Autowired private ClientRepository clientRepository;
    @Autowired private ProjectMasterReferenceRepository projectRepository;
    @Autowired private CurrencyMasterRepository currencyRepository;
    @Autowired private TaxRegionMasterRepository taxRegionRepository;
    @Autowired private TaxTypeMasterRepository taxTypeRepository;
    @Autowired private TaxConfigurationRepository taxConfigurationRepository;
    @Autowired private PaymentTermsMasterRepository paymentTermsRepository;
    @Autowired private BillingFrequencyMasterRepository frequencyRepository;
    @Autowired private BillingTypeMasterRepository billingTypeRepository;
    @Autowired private CompanyProfileRepository companyProfileRepository;
    @Autowired private BillingConfigurationRepository configurationRepository;
    @Autowired private BillingTMRateCardRepository rateCardRepository;
    @Autowired private AutomaticBillingRunRepository runRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private InvoiceApprovalHistoryRepository approvalHistoryRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private ProjectMasterReference project;
    private BillingConfiguration configuration;
    private LocalDate periodStart;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        // No human session: anything asking for the current user fails.
        when(currentUserService.getCurrentUser()).thenThrow(new IllegalStateException("No authenticated user."));
        when(currentUserService.getUserId()).thenThrow(new IllegalStateException("No authenticated user."));
        UMS_MODE.set("ok");
        TMS_MODE.set("ok");
        // Exactly one complete monthly billing period: last month.
        periodStart = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        tx.executeWithoutResult(status -> seed());
    }

    private void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CurrencyMaster inr = currencyRepository.findByCurrencyCodeIgnoreCase("INR").orElseGet(() -> currencyRepository
                .save(CurrencyMaster.builder().currencyCode("INR").currencyName("Indian Rupee").currencySymbol("₹")
                        .isActive(true).build()));
        TaxRegionMaster karnataka = taxRegionRepository.findByTaxRegionNameIgnoreCase("Karnataka").orElseGet(() -> {
            TaxRegionMaster region = taxRegionRepository.save(TaxRegionMaster.builder()
                    .taxRegionCode("KA").taxRegionName("Karnataka").currencyCode("INR").isActive(true).build());
            TaxConfiguration gst = TaxConfiguration.builder().taxRegion(region)
                    .effectiveFrom(LocalDate.of(2020, 1, 1)).isActive(true).components(new ArrayList<>()).build();
            for (String[] component : new String[][] {{"CGST", "9.00", "SAME_JURISDICTION"},
                    {"SGST", "9.00", "SAME_JURISDICTION"}, {"IGST", "18.00", "DIFFERENT_JURISDICTION"}}) {
                TaxTypeMaster type = taxTypeRepository.save(TaxTypeMaster.builder()
                        .taxTypeCode(component[0]).taxTypeName(component[0]).isActive(true).build());
                gst.getComponents().add(TaxConfigurationComponent.builder().taxConfiguration(gst).taxType(type)
                        .taxRate(new BigDecimal(component[1]))
                        .applicabilityType(TaxApplicabilityType.valueOf(component[2])).isActive(true).build());
            }
            taxConfigurationRepository.save(gst);
            return region;
        });
        PaymentTermsMaster net30 = paymentTermsRepository.save(PaymentTermsMaster.builder()
                .paymentTermName("Net 30 " + suffix).paymentDays(30).isActive(true).build());
        BillingFrequencyMaster monthly = frequencyRepository.findByIsActiveTrueOrderByBillingFrequencyNameAsc()
                .stream().filter(f -> f.getBillingFrequencyName().equals("Monthly")).findFirst()
                .orElseGet(() -> frequencyRepository.save(BillingFrequencyMaster.builder()
                        .billingFrequencyName("Monthly").durationUnit(RenewalDurationUnit.MONTHS).durationValue(1)
                        .isActive(true).build()));
        if (companyProfileRepository.findAll().stream().noneMatch(p -> Boolean.TRUE.equals(p.getIsActive()))) {
            companyProfileRepository.save(CompanyProfile.builder().legalName("Test Company Pvt Ltd")
                    .state("Karnataka").country("India").gstin("29ABCDE1234F1Z5").email("billing@test.invalid")
                    .isActive(true).build());
        }
        Client client = clientRepository.save(Client.builder().clientId(UUID.randomUUID())
                .clientName("Test Client " + suffix).email("ap+" + suffix + "@test.invalid").build());
        project = projectRepository.save(ProjectMasterReference.builder()
                .pmsProjectId(Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L))
                .clientId(client.getClientId()).projectName("Test Project " + suffix)
                .primaryLocation("Karnataka").build());
        BillingTypeMaster timesheetBased = billingTypeRepository.findAll().stream()
                .filter(t -> t.getBillingTypeName().equals("Timesheet Based")).findFirst()
                .orElseGet(() -> billingTypeRepository.save(BillingTypeMaster.builder()
                        .billingTypeName("Timesheet Based").isActive(true).build()));
        configuration = configurationRepository.save(BillingConfiguration.builder()
                .client(client).project(project).billingType(timesheetBased).billingFrequency(monthly)
                .currency(inr).taxRegion(karnataka).paymentTerm(net30).pricingModel(PricingModel.ROLE_BASED)
                .effectiveFrom(periodStart).effectiveTo(periodStart.plusMonths(1).minusDays(1))
                .approvalStatus(ApprovalStatus.APPROVED).billingStatus(BillingConfigurationStatus.ACTIVE)
                .invoiceGenerationType(InvoiceGenerationType.AUTOMATIC).manuallyDeactivated(false).build());
        for (Object[] card : new Object[][] {{"Developer", "1500.00"}, {"Senior Developer", "2000.00"}}) {
            rateCardRepository.save(BillingTMRateCard.builder().billingConfiguration(configuration)
                    .roleName((String) card[0]).rate(new BigDecimal((String) card[1]))
                    .ratePeriod(BillingRatePeriod.HOURLY).effectiveFrom(periodStart).isActive(true).build());
        }
    }

    // ================================================================ helpers

    private List<AutomaticBillingRun> runs() {
        return runRepository.findAll().stream()
                .filter(run -> run.getBillingConfiguration().getBillingConfigurationId()
                        .equals(configuration.getBillingConfigurationId()))
                .toList();
    }

    private List<Invoice> invoices() {
        return invoiceRepository.findAll().stream()
                .filter(invoice -> project.getPmsProjectId().equals(invoice.getProjectId()))
                .toList();
    }

    private List<Map<String, String>> tmsRequestsForProject() {
        return TMS_REQUESTS.stream()
                .filter(request -> String.valueOf(project.getPmsProjectId()).equals(request.get("projectId")))
                .toList();
    }

    private List<InvoiceItem> itemsOf(Invoice invoice) {
        return tx.execute(status -> {
            List<InvoiceItem> items = invoiceRepository.findById(invoice.getInvoiceId()).orElseThrow().getItems();
            return new ArrayList<>(items);
        });
    }

    // ================================================================ scenarios

    @Test
    @Order(1)
    void timeAndMaterial_withTheUmsServiceAccount_isBilledToPendingApproval_andARepeatedTickDuplicatesNothing() {
        int loginsBefore = UMS_LOGINS.get();

        scheduler.runOnce();
        scheduler.runOnce();

        // Authentication: one service-account login, its token on every TMS call.
        assertThat(UMS_LOGINS.get() - loginsBefore).isEqualTo(1);
        List<Map<String, String>> tmsCalls = tmsRequestsForProject();
        assertThat(tmsCalls).hasSize(1);
        assertThat(tmsCalls.get(0).get("authorization")).isEqualTo("Bearer " + ISSUED_TOKENS.get(0));
        assertThat(tmsCalls.get(0).get("billingPeriodStart")).isEqualTo(periodStart.toString());
        assertThat(tmsCalls.get(0).get("billingPeriodEnd"))
                .isEqualTo(periodStart.plusMonths(1).minusDays(1).toString());

        // Exactly one run and one invoice, stopped at PENDING_APPROVAL.
        List<AutomaticBillingRun> runs = runs();
        assertThat(runs).hasSize(1);
        AutomaticBillingRun run = runs.get(0);
        assertThat(run.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
        assertThat(run.getLastCompletedStep()).isEqualTo(AutomaticBillingStep.SUBMIT_FOR_APPROVAL);
        assertThat(run.getBillingSnapshotId()).isNotNull();

        List<Invoice> invoices = invoices();
        assertThat(invoices).hasSize(1);
        Invoice invoice = invoices.get(0);
        assertThat(invoice.getInvoiceId()).isEqualTo(run.getInvoiceId());
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING_APPROVAL);
        assertThat(invoice.getBillingSnapshotId()).isEqualTo(run.getBillingSnapshotId());
        assertThat(invoice.getCurrencyCode()).isEqualTo("INR");

        // Lines from the TMS hours at the rate card: 8 x 1500 + 5 x 2000; CGST + SGST 9% each.
        BigDecimal subtotal = new BigDecimal("22000.00");
        BigDecimal halfTax = subtotal.multiply(new BigDecimal("9.00")).divide(new BigDecimal("100"), 2,
                RoundingMode.HALF_UP);
        assertThat(itemsOf(invoice)).extracting(InvoiceItem::getItemType).containsOnly(BillingItemType.TIME_ENTRY);
        assertThat(itemsOf(invoice)).extracting(InvoiceItem::getAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("12000.00"), new BigDecimal("10000.00"));
        assertThat(invoice.getSubtotal()).isEqualByComparingTo(subtotal);
        assertThat(invoice.getTotalTaxAmount()).isEqualByComparingTo(halfTax.add(halfTax));
        assertThat(invoice.getGrandTotal()).isEqualByComparingTo(subtotal.add(halfTax).add(halfTax));

        // Submitted by the system; never approved, never sent.
        List<InvoiceApprovalHistory> history = approvalHistoryRepository
                .findByInvoiceIdOrderByActionAtAsc(invoice.getInvoiceId());
        assertThat(history).extracting(InvoiceApprovalHistory::getAction)
                .containsExactly(InvoiceApprovalAction.SUBMITTED);
        assertThat(history.get(0).getActionBy()).isEqualTo("SYSTEM");
        assertThat(history.get(0).getActionByUserId()).isNull();
        verifyNoInteractions(emailService);
    }

    @Test
    @Order(2)
    void tmsTemporarilyDown_runFailsRetryably_thenCompletesOnALaterTick() throws InterruptedException {
        TMS_MODE.set("down");

        scheduler.runOnce();

        AutomaticBillingRun failed = runs().get(0);
        assertThat(failed.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(failed.getFailureStep()).isEqualTo(AutomaticBillingStep.SOURCE_DATA_ACQUISITION);
        assertThat(failed.getFailureReason()).contains("TMS service failed with status 503");
        assertThat(failed.getNextRetryAt()).isNotNull();
        assertThat(invoices()).isEmpty();
        // Bounded in-call retry: three attempts, then the run-level retry takes over.
        assertThat(tmsRequestsForProject()).hasSize(3);

        TMS_MODE.set("ok");
        while (!LocalDateTime.now().isAfter(failed.getNextRetryAt())) {
            Thread.sleep(100);
        }
        scheduler.runOnce();

        assertThat(runs()).singleElement().satisfies(run -> {
            assertThat(run.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL);
            assertThat(run.getAttemptCount()).isEqualTo(2);
        });
        assertThat(invoices()).singleElement()
                .satisfies(invoice -> assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING_APPROVAL));
        verify(emailService, never()).sendEmailWithAttachment(any(), any(), any(), any(), any(), any());
    }

    @Test
    @Order(3)
    void umsTemporarilyDown_runFailsRetryably_thenCompletesOnceUmsIsBack() throws InterruptedException {
        // TMS refuses the cached token, and the re-login finds UMS unavailable.
        TMS_MODE.set("revoke");
        UMS_MODE.set("down");

        scheduler.runOnce();

        AutomaticBillingRun failed = runs().get(0);
        assertThat(failed.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(failed.getFailureReason()).contains("UMS login temporarily unavailable (status 503)");
        assertThat(failed.getNextRetryAt()).isNotNull();
        assertThat(invoices()).isEmpty();

        TMS_MODE.set("ok");
        UMS_MODE.set("ok");
        while (!LocalDateTime.now().isAfter(failed.getNextRetryAt())) {
            Thread.sleep(100);
        }
        scheduler.runOnce();

        assertThat(runs()).singleElement().satisfies(run ->
                assertThat(run.getStatus()).isEqualTo(AutomaticBillingRunStatus.PENDING_APPROVAL));
        assertThat(invoices()).singleElement()
                .satisfies(invoice -> assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING_APPROVAL));
    }

    @Test
    @Order(4)
    void noEligibleTimesheets_failsWithoutSnapshotOrInvoice_andIsNotRetriedAutomatically() {
        TMS_MODE.set("empty");

        scheduler.runOnce();
        scheduler.runOnce();

        AutomaticBillingRun run = runs().get(0);
        assertThat(run.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(run.getFailureStep()).isEqualTo(AutomaticBillingStep.SOURCE_DATA_ACQUISITION);
        assertThat(run.getFailureReason()).contains("No timesheets were acquired");
        // Current behaviour (documented limitation): timesheets approved later need a manual re-queue.
        assertThat(run.getNextRetryAt()).isNull();
        assertThat(run.getBillingSnapshotId()).isNull();
        assertThat(invoices()).isEmpty();
        // The second tick did not call TMS again for a permanently failed run.
        assertThat(tmsRequestsForProject()).hasSize(1);
    }

    @Test
    @Order(5)
    void timesheetsOfAnotherProject_areRejected_withoutSnapshotOrInvoice() {
        TMS_MODE.set("wrongProject");

        scheduler.runOnce();

        AutomaticBillingRun run = runs().get(0);
        assertThat(run.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(run.getFailureReason()).contains("TMS returned timesheets of project");
        assertThat(run.getNextRetryAt()).isNull();
        assertThat(run.getBillingSnapshotId()).isNull();
        assertThat(invoices()).isEmpty();
    }

    @Test
    @Order(6)
    @DirtiesContext
    void serviceAccountRevokedAndRejected_runFailsPermanently_withoutInvoice() {
        // TMS no longer accepts the cached token; the one re-login is rejected by UMS.
        TMS_MODE.set("revoke");
        UMS_MODE.set("reject");

        scheduler.runOnce();

        AutomaticBillingRun run = runs().get(0);
        assertThat(run.getStatus()).isEqualTo(AutomaticBillingRunStatus.FAILED);
        assertThat(run.getFailureStep()).isEqualTo(AutomaticBillingStep.SOURCE_DATA_ACQUISITION);
        assertThat(run.getFailureReason()).contains("UMS rejected the service-account login")
                .doesNotContain(SERVICE_PASSWORD).doesNotContain(SERVICE_EMAIL);
        // Not retried automatically: credentials must be fixed by a person.
        assertThat(run.getNextRetryAt()).isNull();
        assertThat(invoices()).isEmpty();
        // One TMS call with the refused token; no call after the failed re-login.
        assertThat(tmsRequestsForProject()).hasSize(1);

        int loginsAfterRejection = UMS_LOGINS.get();
        scheduler.runOnce();
        assertThat(UMS_LOGINS.get()).isEqualTo(loginsAfterRejection);
    }
}
