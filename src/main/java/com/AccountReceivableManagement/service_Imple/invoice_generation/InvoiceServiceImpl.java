package com.AccountReceivableManagement.service_Imple.invoice_generation;

import com.AccountReceivableManagement.dto.invoice_generation.InvoiceApprovalHistoryResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceApprovalSummaryResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceApprovalWorkspaceResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceItemResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceNonFinancialCorrectionRequestDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceRejectionRequestDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceSummaryResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceTaxComponentResponseDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshotItem;
import com.AccountReceivableManagement.entity.invoice_generation.Invoice;
import com.AccountReceivableManagement.entity.invoice_generation.InvoiceApprovalHistory;
import com.AccountReceivableManagement.entity.invoice_generation.InvoiceItem;
import com.AccountReceivableManagement.entity.invoice_generation.InvoiceTaxComponent;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.projectbilling_config.PaymentTermsMaster;
import com.AccountReceivableManagement.entity.tax_calculation.TaxCalculation;
import com.AccountReceivableManagement.entity.tax_calculation.TaxCalculationComponent;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceApprovalAction;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxCalculationStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceApprovalHistoryRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.PaymentTermsMasterRepository;
import com.AccountReceivableManagement.repo.tax_calculation.TaxCalculationRepository;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates the frozen, final Invoice for an already tax-completed
 * {@link BillingSnapshot}. Consumes the existing, persisted
 * {@link TaxCalculation} rather than recalculating it: {@code taxableAmount},
 * {@code totalTaxAmount} and {@code grandTotal} are copied as-is and are
 * never recomputed here. Invoice line items and tax components are likewise
 * copied verbatim from the snapshot's {@link BillingSnapshotItem}s and the
 * tax calculation's {@link TaxCalculationComponent}s - no TMS re-query, no
 * new {@link BillingSnapshot}, no call back into the tax engine.
 */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Transactional
public class InvoiceServiceImpl implements InvoiceService {

    private static final DateTimeFormatter INVOICE_NUMBER_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * No authenticated-user context exists anywhere in this backend yet
     * (confirmed against the existing Billing Approval flow in
     * {@code BillingConfigurationServiceImpl}, which likewise records no
     * approver identity). This reuses the same literal convention already
     * used for {@code BillingSnapshot.createdBy}.
     */
    private static final String SYSTEM_ACTION_BY = "SYSTEM";

    /**
     * Recorded as the {@code SUBMITTED} history comment only when a
     * previously {@code REJECTED} invoice is resubmitted, so the audit
     * trail distinguishes a correction resubmission from the original
     * {@code GENERATED -> PENDING_APPROVAL} submission (which keeps its
     * existing {@code null} comment).
     */
    private static final String RESUBMISSION_COMMENT =
            "Invoice resubmitted for approval after correction.";

    /**
     * Recorded as the {@code CORRECTED} history comment by
     * {@link #correctNonFinancialFields(UUID, InvoiceNonFinancialCorrectionRequestDto)},
     * distinct from the fixed comment {@link #refreshAfterCorrection(UUID)}
     * records, so the audit trail can tell a non-financial correction apart
     * from a financial (billing/tax) refresh even though both share the same
     * {@code InvoiceApprovalAction.CORRECTED} action and satisfy the same
     * {@code correctionRequired} check.
     */
    private static final String NON_FINANCIAL_CORRECTION_COMMENT =
            "Non-financial invoice correction completed.";

    /**
     * Matches {@code Invoice.clientName}/{@code Invoice.projectName}'s
     * {@code length = 255} column definition - kept here as an explicit
     * service-layer guard since this project has no
     * {@code MethodArgumentNotValidException} handler wired to return 400
     * for a persistence-time value-too-long failure.
     */
    private static final int CLIENT_OR_PROJECT_NAME_MAX_LENGTH = 255;

    private final InvoiceRepository invoiceRepository;

    private final InvoiceApprovalHistoryRepository invoiceApprovalHistoryRepository;

    private final BillingSnapshotRepository billingSnapshotRepository;

    private final TaxCalculationRepository taxCalculationRepository;

    private final BillingConfigurationService billingConfigurationService;

    private final PaymentTermsMasterRepository paymentTermsMasterRepository;

        private final BillingScheduleRepository billingScheduleRepository;

        public InvoiceServiceImpl(
                        InvoiceRepository invoiceRepository,
                        InvoiceApprovalHistoryRepository invoiceApprovalHistoryRepository,
                        BillingSnapshotRepository billingSnapshotRepository,
                        TaxCalculationRepository taxCalculationRepository,
                        BillingConfigurationService billingConfigurationService,
                        PaymentTermsMasterRepository paymentTermsMasterRepository
        ) {
                this.invoiceRepository = invoiceRepository;
                this.invoiceApprovalHistoryRepository = invoiceApprovalHistoryRepository;
                this.billingSnapshotRepository = billingSnapshotRepository;
                this.taxCalculationRepository = taxCalculationRepository;
                this.billingConfigurationService = billingConfigurationService;
                this.paymentTermsMasterRepository = paymentTermsMasterRepository;
                this.billingScheduleRepository = null;
        }

    @Override
    public InvoiceResponseDto generateInvoice(
            UUID billingSnapshotId
    ) {

        BillingSnapshot snapshot =
                billingSnapshotRepository.findById(
                                billingSnapshotId
                        )
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "Billing snapshot could not be found."
                                )
                        );

        if (snapshot.getStatus()
                != BillingSnapshotStatus.TAX_COMPLETED) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Invoice cannot be generated because this billing snapshot has not completed tax calculation."
            );
        }

        TaxCalculation taxCalculation =
                taxCalculationRepository
                        .findByBillingSnapshotId(
                                billingSnapshotId
                        )
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "No tax calculation has been completed for this billing snapshot. Invoice cannot be generated."
                                )
                        );

        if (invoiceRepository
                .existsByBillingSnapshotId(
                        billingSnapshotId
                )) {

            throw new GlobalExceptionHandler
                    .DuplicateResourceException(
                    "An invoice has already been generated for this billing snapshot."
            );
        }

        /*
         * Authority hierarchy: the persisted TaxCalculation is the sole
         * source of the invoice's financial totals. Values are copied
         * as-is, never recomputed. The only check performed here is an
         * internal consistency guard - if the tax calculation's own totals
         * don't add up, invoice generation fails loudly instead of
         * silently "correcting" or recalculating anything.
         */
        validateTaxCalculationConsistency(
                taxCalculation,
                "Tax calculation totals are inconsistent for this billing snapshot. Invoice cannot be generated."
        );

        BillingConfigurationResponseDto configuration =
                billingConfigurationService
                        .getBillingConfiguration(
                                snapshot.getBillingConfigurationId()
                        );

        LocalDate invoiceDate = LocalDate.now();

        Invoice invoice =
                Invoice.builder()
                        .invoiceNumber(generateInvoiceNumber())
                        .billingSnapshotId(snapshot.getId())
                        .billingSnapshotNumber(
                                snapshot.getSnapshotNumber()
                        )
                        .taxCalculationId(
                                taxCalculation.getTaxCalculationId()
                        )
                        .clientId(snapshot.getClientId())
                        .clientName(
                                configuration.getClientName()
                        )
                        .projectId(snapshot.getProjectId())
                        .projectName(
                                configuration.getProjectName()
                        )
                        .billingPeriodStart(
                                snapshot.getBillingPeriodStart()
                        )
                        .billingPeriodEnd(
                                snapshot.getBillingPeriodEnd()
                        )
                        .currencyCode(snapshot.getCurrencyCode())
                        .paymentTermCode(
                                snapshot.getPaymentTermCode()
                        )
                        .subtotal(
                                taxCalculation.getTaxableAmount()
                        )
                        .totalTaxAmount(
                                taxCalculation.getTotalTaxAmount()
                        )
                        .grandTotal(
                                taxCalculation.getGrandTotal()
                        )
                        .invoiceDate(invoiceDate)
                        .dueDate(
                                resolveDueDate(
                                        snapshot,
                                        invoiceDate
                                )
                        )
                        .generatedAt(LocalDateTime.now())
                        .status(InvoiceStatus.GENERATED)
                        .build();

        for (
                BillingSnapshotItem snapshotItem
                : snapshot.getItems()
        ) {

            InvoiceItem invoiceItem =
                    InvoiceItem.builder()
                            .invoice(invoice)
                            .itemType(
                                    snapshotItem.getItemType()
                            )
                            .itemName(
                                    snapshotItem.getItemName()
                            )
                            .sourceReferenceId(
                                    snapshotItem
                                            .getSourceReferenceId()
                            )
                            .quantity(
                                    snapshotItem.getQuantity()
                            )
                            .rate(snapshotItem.getRate())
                            .amount(snapshotItem.getAmount())
                            .workDate(
                                    snapshotItem.getWorkDate()
                            )
                            .role(snapshotItem.getRole())
                            .build();

            invoice.getItems().add(invoiceItem);
        }

        for (
                TaxCalculationComponent taxComponent
                : taxCalculation.getComponents()
        ) {

            InvoiceTaxComponent invoiceTaxComponent =
                    InvoiceTaxComponent.builder()
                            .invoice(invoice)
                            .taxTypeId(
                                    taxComponent.getTaxTypeId()
                            )
                            .taxTypeCode(
                                    taxComponent.getTaxTypeCode()
                            )
                            .taxTypeName(
                                    taxComponent.getTaxTypeName()
                            )
                            .appliedRate(
                                    taxComponent.getAppliedRate()
                            )
                            .taxAmount(
                                    taxComponent.getTaxAmount()
                            )
                            .applicabilityType(
                                    taxComponent
                                            .getApplicabilityType()
                            )
                            .build();

            invoice.getTaxComponents()
                    .add(invoiceTaxComponent);
        }

        Invoice saved;

        try {

            saved = invoiceRepository.save(invoice);

        } catch (DataIntegrityViolationException ex) {

            throw new GlobalExceptionHandler
                    .DuplicateResourceException(
                    "An invoice has already been generated for this billing snapshot."
            );
        }

        snapshot.setStatus(
                BillingSnapshotStatus.INVOICED
        );

        billingSnapshotRepository.save(snapshot);

        return mapToResponse(saved);
    }

    @Override
    public InvoiceResponseDto generateInvoiceForSchedule(UUID billingScheduleId) {

        BillingSchedule schedule =
                billingScheduleRepository.findByIdForUpdate(billingScheduleId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Billing occurrence could not be found."
                                ));

        if (!Boolean.TRUE.equals(schedule.getIsActive())) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Invoice cannot be generated because this billing occurrence is inactive."
            );
        }

        if (schedule.getPeriodStatus() != BillingPeriodStatus.TAX_CALCULATED
                || schedule.getTaxStatus() != BillingPeriodStatus.TAX_CALCULATED) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Invoice cannot be generated because this billing occurrence has not completed tax calculation."
            );
        }

        if (Boolean.TRUE.equals(schedule.getIsInvoiced())) {
            throw new GlobalExceptionHandler.DuplicateResourceException(
                    "An invoice has already been generated for this billing occurrence."
            );
        }

        TaxCalculation taxCalculation =
                taxCalculationRepository.findByBillingScheduleId(billingScheduleId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "No tax calculation has been completed for this billing occurrence. Invoice cannot be generated."
                                ));

        if (taxCalculation.getStatus() != TaxCalculationStatus.CALCULATED) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Invoice cannot be generated because the tax calculation has not completed successfully."
            );
        }

        if (invoiceRepository.existsByBillingScheduleId(billingScheduleId)) {
            throw new GlobalExceptionHandler.DuplicateResourceException(
                    "An invoice has already been generated for this billing occurrence."
            );
        }

        validateTaxCalculationConsistency(
                taxCalculation,
                "Tax calculation totals are inconsistent for this billing occurrence. Invoice cannot be generated."
        );

        BillingConfigurationResponseDto configuration =
                billingConfigurationService.getBillingConfiguration(
                        schedule.getBillingConfiguration().getBillingConfigurationId()
                );

        LocalDate invoiceDate = LocalDate.now();
        BigDecimal billingAmount = schedule.getBillingAmount();

        Invoice invoice =
                Invoice.builder()
                        .invoiceNumber(generateInvoiceNumber())
                        .billingScheduleId(schedule.getBillingScheduleId())
                        .taxCalculationId(taxCalculation.getTaxCalculationId())
                        .clientId(configuration.getClientId())
                        .clientName(configuration.getClientName())
                        .projectId(configuration.getProjectId())
                        .projectName(configuration.getProjectName())
                        .billingPeriodStart(schedule.getPeriodStartDate())
                        .billingPeriodEnd(schedule.getPeriodEndDate())
                        .currencyCode(configuration.getCurrencyCode())
                        .paymentTermCode(configuration.getPaymentTermCode())
                        .subtotal(taxCalculation.getTaxableAmount())
                        .totalTaxAmount(taxCalculation.getTotalTaxAmount())
                        .grandTotal(taxCalculation.getGrandTotal())
                        .invoiceDate(invoiceDate)
                        .dueDate(resolveDueDate(configuration.getPaymentTermId(), invoiceDate))
                        .generatedAt(LocalDateTime.now())
                        .status(InvoiceStatus.GENERATED)
                        .build();

        invoice.getItems().add(
                InvoiceItem.builder()
                        .invoice(invoice)
                        .itemType(BillingItemType.FIXED_PRICE)
                        .itemName("Fixed Price - Period " + schedule.getPeriodNumber())
                        .sourceReferenceId(schedule.getBillingScheduleId().toString())
                        .quantity(BigDecimal.ONE)
                        .rate(billingAmount)
                        .amount(billingAmount)
                        .build()
        );

        for (TaxCalculationComponent taxComponent : taxCalculation.getComponents()) {
            invoice.getTaxComponents().add(
                    InvoiceTaxComponent.builder()
                            .invoice(invoice)
                            .taxTypeId(taxComponent.getTaxTypeId())
                            .taxTypeCode(taxComponent.getTaxTypeCode())
                            .taxTypeName(taxComponent.getTaxTypeName())
                            .appliedRate(taxComponent.getAppliedRate())
                            .taxAmount(taxComponent.getTaxAmount())
                            .applicabilityType(taxComponent.getApplicabilityType())
                            .build()
            );
        }

        Invoice saved;
        try {
            saved = invoiceRepository.save(invoice);
        } catch (DataIntegrityViolationException ex) {
            throw new GlobalExceptionHandler.DuplicateResourceException(
                    "An invoice has already been generated for this billing occurrence."
            );
        }

        schedule.setIsInvoiced(true);
        schedule.setInvoiceDate(invoiceDate);
        schedule.setPeriodStatus(BillingPeriodStatus.INVOICED);
        billingScheduleRepository.save(schedule);

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponseDto getInvoiceByBillingSnapshotId(
            UUID billingSnapshotId
    ) {

        if (!billingSnapshotRepository.existsById(
                billingSnapshotId
        )) {

            throw new GlobalExceptionHandler
                    .ResourceNotFoundException(
                    "Billing snapshot could not be found."
            );
        }

        Invoice invoice =
                invoiceRepository
                        .findByBillingSnapshotId(
                                billingSnapshotId
                        )
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "No invoice has been generated for this billing snapshot."
                                )
                        );

        return mapToResponse(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceSummaryResponseDto> getAllInvoices() {

        return invoiceRepository
                .findAllByOrderByGeneratedAtDesc()
                .stream()
                .map(this::mapToSummary)
                .toList();
    }

    @Override
    public InvoiceResponseDto submitForApproval(
            UUID invoiceId
    ) {

        Invoice invoice =
                invoiceRepository.findById(invoiceId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "Invoice could not be found."
                                )
                        );

        InvoiceStatus previousStatus = invoice.getStatus();

        if (previousStatus != InvoiceStatus.GENERATED
                && previousStatus != InvoiceStatus.REJECTED) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Only invoices with status GENERATED or REJECTED can be submitted for approval."
            );
        }

        if (previousStatus == InvoiceStatus.REJECTED
                && resolveCorrectionState(
                        invoiceId,
                        InvoiceStatus.REJECTED
                ).correctionRequired()) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Invoice must be refreshed after correction before resubmission."
            );
        }

        invoice.setStatus(InvoiceStatus.PENDING_APPROVAL);

        Invoice saved = invoiceRepository.save(invoice);

        recordHistory(
                saved.getInvoiceId(),
                previousStatus,
                InvoiceStatus.PENDING_APPROVAL,
                InvoiceApprovalAction.SUBMITTED,
                previousStatus == InvoiceStatus.REJECTED
                        ? RESUBMISSION_COMMENT
                        : null
        );

        return mapToResponse(saved);
    }

    @Override
    public InvoiceResponseDto rejectInvoice(
            UUID invoiceId,
            InvoiceRejectionRequestDto request
    ) {

        if (request.getReason() == null
                || request.getReason().isBlank()) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Rejection reason is required."
            );
        }

        Invoice invoice =
                invoiceRepository.findById(invoiceId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "Invoice could not be found."
                                )
                        );

        if (invoice.getStatus()
                != InvoiceStatus.PENDING_APPROVAL) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Only invoices pending approval can be rejected."
            );
        }

        InvoiceStatus previousStatus = invoice.getStatus();

        invoice.setStatus(InvoiceStatus.REJECTED);

        Invoice saved = invoiceRepository.save(invoice);

        recordHistory(
                saved.getInvoiceId(),
                previousStatus,
                InvoiceStatus.REJECTED,
                InvoiceApprovalAction.REJECTED,
                request.getReason()
        );

        return mapToResponse(saved);
    }

    @Override
    public InvoiceResponseDto refreshAfterCorrection(
            UUID invoiceId
    ) {

        Invoice invoice =
                invoiceRepository.findById(invoiceId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "Invoice could not be found."
                                )
                        );

        if (invoice.getStatus()
                != InvoiceStatus.REJECTED) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Only rejected invoices can be refreshed after correction."
            );
        }

        BillingSnapshot snapshot =
                billingSnapshotRepository
                        .findById(invoice.getBillingSnapshotId())
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "Billing snapshot could not be found."
                                )
                        );

        if (snapshot.getStatus() != BillingSnapshotStatus.TAX_COMPLETED
                && snapshot.getStatus() != BillingSnapshotStatus.INVOICED) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Invoice cannot be refreshed because this billing snapshot has not completed tax calculation."
            );
        }

        TaxCalculation taxCalculation =
                taxCalculationRepository
                        .findByBillingSnapshotId(
                                invoice.getBillingSnapshotId()
                        )
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "No tax calculation has been completed for this billing snapshot. Invoice cannot be refreshed."
                                )
                        );

        if (taxCalculation.getStatus()
                != TaxCalculationStatus.CALCULATED) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Invoice cannot be refreshed because tax calculation has not completed successfully."
            );
        }

        validateTaxCalculationConsistency(
                taxCalculation,
                "Tax calculation totals are inconsistent for this billing snapshot. Invoice cannot be refreshed."
        );

        refreshInvoiceFromAuthoritativeData(invoice, snapshot, taxCalculation);

        snapshot.setStatus(BillingSnapshotStatus.INVOICED);

        billingSnapshotRepository.save(snapshot);

        Invoice saved = invoiceRepository.save(invoice);

        recordHistory(
                saved.getInvoiceId(),
                InvoiceStatus.REJECTED,
                InvoiceStatus.REJECTED,
                InvoiceApprovalAction.CORRECTED,
                "Invoice refreshed from corrected billing/tax data."
        );

        return mapToResponse(saved);
    }

    /**
     * Re-copies every financial field, line item, and tax component onto
     * the existing, already-persisted Invoice from its authoritative
     * BillingSnapshot/TaxCalculation - the same copy-as-is population used
     * by {@link #generateInvoice(UUID)}, applied in place instead of to a
     * newly built Invoice. {@code invoiceId}, {@code invoiceNumber}, and
     * {@code status} are never touched here.
     */
    private void refreshInvoiceFromAuthoritativeData(
            Invoice invoice,
            BillingSnapshot snapshot,
            TaxCalculation taxCalculation
    ) {

        invoice.setBillingSnapshotNumber(
                snapshot.getSnapshotNumber()
        );
        invoice.setTaxCalculationId(
                taxCalculation.getTaxCalculationId()
        );
        invoice.setBillingPeriodStart(
                snapshot.getBillingPeriodStart()
        );
        invoice.setBillingPeriodEnd(
                snapshot.getBillingPeriodEnd()
        );
        invoice.setCurrencyCode(snapshot.getCurrencyCode());
        invoice.setPaymentTermCode(
                snapshot.getPaymentTermCode()
        );
        invoice.setSubtotal(
                taxCalculation.getTaxableAmount()
        );
        invoice.setTotalTaxAmount(
                taxCalculation.getTotalTaxAmount()
        );
        invoice.setGrandTotal(
                taxCalculation.getGrandTotal()
        );
        invoice.setDueDate(
                resolveDueDate(
                        snapshot,
                        invoice.getInvoiceDate()
                )
        );

        invoice.getItems().clear();

        for (
                BillingSnapshotItem snapshotItem
                : snapshot.getItems()
        ) {

            invoice.getItems().add(
                    InvoiceItem.builder()
                            .invoice(invoice)
                            .itemType(
                                    snapshotItem.getItemType()
                            )
                            .itemName(
                                    snapshotItem.getItemName()
                            )
                            .sourceReferenceId(
                                    snapshotItem
                                            .getSourceReferenceId()
                            )
                            .quantity(
                                    snapshotItem.getQuantity()
                            )
                            .rate(snapshotItem.getRate())
                            .amount(snapshotItem.getAmount())
                            .workDate(
                                    snapshotItem.getWorkDate()
                            )
                            .role(snapshotItem.getRole())
                            .build()
            );
        }

        invoice.getTaxComponents().clear();

        for (
                TaxCalculationComponent taxComponent
                : taxCalculation.getComponents()
        ) {

            invoice.getTaxComponents().add(
                    InvoiceTaxComponent.builder()
                            .invoice(invoice)
                            .taxTypeId(
                                    taxComponent.getTaxTypeId()
                            )
                            .taxTypeCode(
                                    taxComponent.getTaxTypeCode()
                            )
                            .taxTypeName(
                                    taxComponent.getTaxTypeName()
                            )
                            .appliedRate(
                                    taxComponent.getAppliedRate()
                            )
                            .taxAmount(
                                    taxComponent.getTaxAmount()
                            )
                            .applicabilityType(
                                    taxComponent
                                            .getApplicabilityType()
                            )
                            .build()
            );
        }
    }

    @Override
    public InvoiceResponseDto correctNonFinancialFields(
            UUID invoiceId,
            InvoiceNonFinancialCorrectionRequestDto request
    ) {

        String clientName = validateAndTrimName(
                request.getClientName(),
                "Client name is required.",
                "Client name must not exceed "
                        + CLIENT_OR_PROJECT_NAME_MAX_LENGTH
                        + " characters."
        );

        String projectName = validateAndTrimName(
                request.getProjectName(),
                "Project name is required.",
                "Project name must not exceed "
                        + CLIENT_OR_PROJECT_NAME_MAX_LENGTH
                        + " characters."
        );

        Invoice invoice =
                invoiceRepository.findById(invoiceId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "Invoice could not be found."
                                )
                        );

        if (invoice.getStatus()
                != InvoiceStatus.REJECTED) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Only rejected invoices can be corrected."
            );
        }

        if (!resolveCorrectionState(
                invoiceId,
                InvoiceStatus.REJECTED
        ).correctionRequired()) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Invoice has already been corrected since its latest rejection. Wait for a new rejection before correcting again."
            );
        }

        invoice.setClientName(clientName);
        invoice.setProjectName(projectName);

        Invoice saved = invoiceRepository.save(invoice);

        recordHistory(
                saved.getInvoiceId(),
                InvoiceStatus.REJECTED,
                InvoiceStatus.REJECTED,
                InvoiceApprovalAction.CORRECTED,
                NON_FINANCIAL_CORRECTION_COMMENT
        );

        return mapToResponse(saved);
    }

    /**
     * Shared blank/length guard for {@code clientName}/{@code projectName}
     * in {@link #correctNonFinancialFields(UUID, InvoiceNonFinancialCorrectionRequestDto)} -
     * mirrors the manual validation style already used by
     * {@link #rejectInvoice(UUID, InvoiceRejectionRequestDto)} rather than
     * relying on {@code @Valid} binding.
     */
    private String validateAndTrimName(
            String value,
            String blankMessage,
            String tooLongMessage
    ) {

        if (value == null || value.isBlank()) {

            throw new GlobalExceptionHandler
                    .ValidationException(blankMessage);
        }

        String trimmed = value.trim();

        if (trimmed.length() > CLIENT_OR_PROJECT_NAME_MAX_LENGTH) {

            throw new GlobalExceptionHandler
                    .ValidationException(tooLongMessage);
        }

        return trimmed;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCorrectionRequired(
            UUID invoiceId
    ) {

        Invoice invoice =
                invoiceRepository.findById(invoiceId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "Invoice could not be found."
                                )
                        );

        return resolveCorrectionState(
                invoiceId,
                invoice.getStatus()
        ).correctionRequired();
    }

    @Override
    public InvoiceResponseDto approveInvoice(
            UUID invoiceId
    ) {

        Invoice invoice =
                invoiceRepository.findById(invoiceId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "Invoice could not be found."
                                )
                        );

        if (invoice.getStatus()
                != InvoiceStatus.PENDING_APPROVAL) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Only invoices pending approval can be approved."
            );
        }

        InvoiceStatus previousStatus = invoice.getStatus();

        invoice.setStatus(InvoiceStatus.APPROVED);

        Invoice saved = invoiceRepository.save(invoice);

        recordHistory(
                saved.getInvoiceId(),
                previousStatus,
                InvoiceStatus.APPROVED,
                InvoiceApprovalAction.APPROVED,
                null
        );

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceApprovalHistoryResponseDto> getApprovalHistory(
            UUID invoiceId
    ) {

        if (!invoiceRepository.existsById(invoiceId)) {

            throw new GlobalExceptionHandler
                    .ResourceNotFoundException(
                    "Invoice could not be found."
            );
        }

        return invoiceApprovalHistoryRepository
                .findByInvoiceIdOrderByActionAtAsc(invoiceId)
                .stream()
                .map(this::mapToHistoryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceApprovalSummaryResponseDto> getPendingApprovalInvoices() {

        return invoiceRepository
                .findAllByStatusOrderByGeneratedAtDesc(
                        InvoiceStatus.PENDING_APPROVAL
                )
                .stream()
                .map(this::mapToApprovalSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceApprovalWorkspaceResponseDto> getApprovalWorkspaceInvoices() {

        List<Invoice> invoices =
                invoiceRepository
                        .findAllInApprovalWorkflowOrderByGeneratedAtDesc();

        if (invoices.isEmpty()) {
            return List.of();
        }

        List<UUID> invoiceIds =
                invoices.stream()
                        .map(Invoice::getInvoiceId)
                        .toList();

        Map<UUID, List<InvoiceApprovalHistory>> historyByInvoiceId =
                invoiceApprovalHistoryRepository
                        .findByInvoiceIdInOrderByActionAtAsc(invoiceIds)
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        InvoiceApprovalHistory::getInvoiceId
                                )
                        );

        return invoices.stream()
                .map(invoice ->
                        mapToWorkspaceSummary(
                                invoice,
                                historyByInvoiceId.getOrDefault(
                                        invoice.getInvoiceId(),
                                        List.of()
                                )
                        )
                )
                .toList();
    }

    /**
     * Defensive guard only - does not recompute anything. Fails invoice
     * generation (or, reused, invoice correction-refresh) if the persisted
     * TaxCalculation's own totals are internally inconsistent, rather than
     * silently correcting them.
     */
    private void validateTaxCalculationConsistency(
            TaxCalculation taxCalculation,
            String inconsistencyMessage
    ) {

        BigDecimal expectedGrandTotal =
                taxCalculation.getTaxableAmount()
                        .add(taxCalculation.getTotalTaxAmount());

        if (expectedGrandTotal.compareTo(
                taxCalculation.getGrandTotal()
        ) != 0) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    inconsistencyMessage
            );
        }
    }

    private String generateInvoiceNumber() {
        return "INV-" + LocalDateTime.now()
                .format(INVOICE_NUMBER_FORMATTER);
    }

    /**
     * Resolves the due date from the billing snapshot's own (frozen)
     * payment term. Returns {@code null} - never a guessed value - when the
     * snapshot has no payment term or it cannot be resolved to a
     * {@code paymentDays} figure.
     */
    private LocalDate resolveDueDate(
            BillingSnapshot snapshot,
            LocalDate invoiceDate
    ) {

        if (snapshot.getPaymentTermId() == null) {
            return null;
        }

        return paymentTermsMasterRepository
                .findById(snapshot.getPaymentTermId())
                .map(PaymentTermsMaster::getPaymentDays)
                .map(invoiceDate::plusDays)
                .orElse(null);
    }

        private LocalDate resolveDueDate(
                        UUID paymentTermId,
                        LocalDate invoiceDate
        ) {

        if (paymentTermId == null) {
            return null;
        }

        return paymentTermsMasterRepository
                .findById(paymentTermId)
                .map(PaymentTermsMaster::getPaymentDays)
                .map(invoiceDate::plusDays)
                .orElse(null);
    }

    private InvoiceResponseDto mapToResponse(
            Invoice invoice
    ) {

        List<InvoiceItemResponseDto> items =
                invoice.getItems()
                        .stream()
                        .map(item ->
                                InvoiceItemResponseDto.builder()
                                        .invoiceItemId(
                                                item.getInvoiceItemId()
                                        )
                                        .itemType(item.getItemType())
                                        .itemName(item.getItemName())
                                        .sourceReferenceId(
                                                item
                                                        .getSourceReferenceId()
                                        )
                                        .quantity(item.getQuantity())
                                        .rate(item.getRate())
                                        .amount(item.getAmount())
                                        .workDate(item.getWorkDate())
                                        .role(item.getRole())
                                        .build()
                        )
                        .toList();

        List<InvoiceTaxComponentResponseDto> taxComponents =
                invoice.getTaxComponents()
                        .stream()
                        .map(component ->
                                InvoiceTaxComponentResponseDto
                                        .builder()
                                        .invoiceTaxComponentId(
                                                component
                                                        .getInvoiceTaxComponentId()
                                        )
                                        .taxTypeId(
                                                component.getTaxTypeId()
                                        )
                                        .taxTypeCode(
                                                component
                                                        .getTaxTypeCode()
                                        )
                                        .taxTypeName(
                                                component
                                                        .getTaxTypeName()
                                        )
                                        .appliedRate(
                                                component
                                                        .getAppliedRate()
                                        )
                                        .taxAmount(
                                                component.getTaxAmount()
                                        )
                                        .applicabilityType(
                                                component
                                                        .getApplicabilityType()
                                        )
                                        .build()
                        )
                        .toList();

        CorrectionState correctionState =
                resolveCorrectionState(
                        invoice.getInvoiceId(),
                        invoice.getStatus()
                );

        return InvoiceResponseDto.builder()
                .invoiceId(invoice.getInvoiceId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .status(invoice.getStatus())
                .billingSnapshotId(
                        invoice.getBillingSnapshotId()
                )
                .billingScheduleId(invoice.getBillingScheduleId())
                .billingSnapshotNumber(
                        invoice.getBillingSnapshotNumber()
                )
                .taxCalculationId(
                        invoice.getTaxCalculationId()
                )
                .clientId(invoice.getClientId())
                .clientName(invoice.getClientName())
                .billingAddress(invoice.getBillingAddress())
                .gstinOrTaxId(invoice.getGstinOrTaxId())
                .contact(invoice.getContact())
                .projectId(invoice.getProjectId())
                .projectName(invoice.getProjectName())
                .billingPeriodStart(
                        invoice.getBillingPeriodStart()
                )
                .billingPeriodEnd(
                        invoice.getBillingPeriodEnd()
                )
                .currencyCode(invoice.getCurrencyCode())
                .paymentTermCode(invoice.getPaymentTermCode())
                .invoiceDate(invoice.getInvoiceDate())
                .dueDate(invoice.getDueDate())
                .items(items)
                .taxComponents(taxComponents)
                .subtotal(invoice.getSubtotal())
                .totalTaxAmount(invoice.getTotalTaxAmount())
                .grandTotal(invoice.getGrandTotal())
                .generatedAt(invoice.getGeneratedAt())
                .correctionRequired(
                        correctionState.correctionRequired()
                )
                .lastCorrectedAt(
                        correctionState.lastCorrectedAt()
                )
                .build();
    }

    /**
     * Maps one row of {@code GET /api/v1/invoices} directly from the
     * persisted Invoice - every value copied as-is, nothing recalculated.
     */
    private InvoiceSummaryResponseDto mapToSummary(
            Invoice invoice
    ) {

        return InvoiceSummaryResponseDto.builder()
                .invoiceId(invoice.getInvoiceId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .status(invoice.getStatus())
                .billingSnapshotId(
                        invoice.getBillingSnapshotId()
                )
                .billingSnapshotNumber(
                        invoice.getBillingSnapshotNumber()
                )
                .clientName(invoice.getClientName())
                .projectName(invoice.getProjectName())
                .billingPeriodStart(
                        invoice.getBillingPeriodStart()
                )
                .billingPeriodEnd(
                        invoice.getBillingPeriodEnd()
                )
                .invoiceDate(invoice.getInvoiceDate())
                .dueDate(invoice.getDueDate())
                .currencyCode(invoice.getCurrencyCode())
                .paymentTermCode(invoice.getPaymentTermCode())
                .subtotal(invoice.getSubtotal())
                .totalTaxAmount(invoice.getTotalTaxAmount())
                .grandTotal(invoice.getGrandTotal())
                .build();
    }

    /**
     * Appended independently of the Invoice's own save - not cascaded
     * through it - matching the standalone-audit-table convention already
     * used by {@code SoftwareBillingHistory}.
     */
    private void recordHistory(
            UUID invoiceId,
            InvoiceStatus previousStatus,
            InvoiceStatus newStatus,
            InvoiceApprovalAction action,
            String comment
    ) {

        InvoiceApprovalHistory history =
                InvoiceApprovalHistory.builder()
                        .invoiceId(invoiceId)
                        .previousStatus(previousStatus)
                        .newStatus(newStatus)
                        .action(action)
                        .actionBy(SYSTEM_ACTION_BY)
                        .actionAt(LocalDateTime.now())
                        .comment(comment)
                        .build();

        invoiceApprovalHistoryRepository.save(history);
    }

    private InvoiceApprovalHistoryResponseDto mapToHistoryResponse(
            InvoiceApprovalHistory history
    ) {

        return InvoiceApprovalHistoryResponseDto.builder()
                .action(history.getAction())
                .previousStatus(history.getPreviousStatus())
                .newStatus(history.getNewStatus())
                .actionBy(history.getActionBy())
                .actionAt(history.getActionAt())
                .comment(history.getComment())
                .build();
    }

    /**
     * Maps one row of {@code GET /api/v1/invoices/pending-approval} directly
     * from the persisted Invoice, enriched with its latest SUBMITTED
     * history entry - nothing recalculated.
     */
    private InvoiceApprovalSummaryResponseDto mapToApprovalSummary(
            Invoice invoice
    ) {

        InvoiceApprovalHistory latestSubmission =
                invoiceApprovalHistoryRepository
                        .findTopByInvoiceIdAndActionOrderByActionAtDesc(
                                invoice.getInvoiceId(),
                                InvoiceApprovalAction.SUBMITTED
                        )
                        .orElse(null);

        return InvoiceApprovalSummaryResponseDto.builder()
                .invoiceId(invoice.getInvoiceId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .status(invoice.getStatus())
                .billingSnapshotId(
                        invoice.getBillingSnapshotId()
                )
                .billingSnapshotNumber(
                        invoice.getBillingSnapshotNumber()
                )
                .clientName(invoice.getClientName())
                .projectName(invoice.getProjectName())
                .billingPeriodStart(
                        invoice.getBillingPeriodStart()
                )
                .billingPeriodEnd(
                        invoice.getBillingPeriodEnd()
                )
                .invoiceDate(invoice.getInvoiceDate())
                .dueDate(invoice.getDueDate())
                .currencyCode(invoice.getCurrencyCode())
                .grandTotal(invoice.getGrandTotal())
                .submittedAt(
                        latestSubmission != null
                                ? latestSubmission.getActionAt()
                                : null
                )
                .submittedBy(
                        latestSubmission != null
                                ? latestSubmission.getActionBy()
                                : null
                )
                .build();
    }

    /**
     * Maps one row of {@code GET /api/v1/invoices/approval-workspace}
     * directly from the persisted Invoice, enriched from its already
     * bulk-fetched history (ascending by actionAt) - no per-invoice query,
     * nothing recalculated.
     */
    private InvoiceApprovalWorkspaceResponseDto mapToWorkspaceSummary(
            Invoice invoice,
            List<InvoiceApprovalHistory> history
    ) {

        InvoiceApprovalHistory latestSubmission = null;
        InvoiceApprovalHistory latestAction = null;

        for (InvoiceApprovalHistory entry : history) {

            if (entry.getAction()
                    == InvoiceApprovalAction.SUBMITTED) {
                latestSubmission = entry;
            }

            latestAction = entry;
        }

        CorrectionState correctionState =
                invoice.getStatus() == InvoiceStatus.REJECTED
                        ? computeCorrectionState(history)
                        : new CorrectionState(false, null);

        return InvoiceApprovalWorkspaceResponseDto.builder()
                .invoiceId(invoice.getInvoiceId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .status(invoice.getStatus())
                .billingSnapshotId(
                        invoice.getBillingSnapshotId()
                )
                .billingSnapshotNumber(
                        invoice.getBillingSnapshotNumber()
                )
                .clientName(invoice.getClientName())
                .projectName(invoice.getProjectName())
                .billingPeriodStart(
                        invoice.getBillingPeriodStart()
                )
                .billingPeriodEnd(
                        invoice.getBillingPeriodEnd()
                )
                .invoiceDate(invoice.getInvoiceDate())
                .dueDate(invoice.getDueDate())
                .currencyCode(invoice.getCurrencyCode())
                .grandTotal(invoice.getGrandTotal())
                .submittedAt(
                        latestSubmission != null
                                ? latestSubmission.getActionAt()
                                : null
                )
                .submittedBy(
                        latestSubmission != null
                                ? latestSubmission.getActionBy()
                                : null
                )
                .lastAction(
                        latestAction != null
                                ? latestAction.getAction()
                                : null
                )
                .lastActionAt(
                        latestAction != null
                                ? latestAction.getActionAt()
                                : null
                )
                .correctionRequired(
                        correctionState.correctionRequired()
                )
                .lastCorrectedAt(
                        correctionState.lastCorrectedAt()
                )
                .build();
    }

    /**
     * {@code correctionRequired} is {@code true} only when the invoice is
     * currently {@code REJECTED} and no {@code CORRECTED} history entry has
     * been recorded since the latest {@code REJECTED} entry - i.e. a prior
     * correction from an earlier rejection cycle does not satisfy a newer
     * rejection (see {@link #submitForApproval(UUID)}). Fetches history
     * itself - only called for a {@code REJECTED} invoice, so this is a
     * single extra query on an already-infrequent path, never inside a bulk
     * listing.
     */
    private CorrectionState resolveCorrectionState(
            UUID invoiceId,
            InvoiceStatus status
    ) {

        if (status != InvoiceStatus.REJECTED) {
            return new CorrectionState(false, null);
        }

        return computeCorrectionState(
                invoiceApprovalHistoryRepository
                        .findByInvoiceIdOrderByActionAtAsc(invoiceId)
        );
    }

    /**
     * Pure computation over an already-fetched, ascending-by-actionAt
     * history list - used both by {@link #resolveCorrectionState(UUID,
     * InvoiceStatus)} (single-invoice paths) and by
     * {@link #mapToWorkspaceSummary(Invoice, List)} (which already
     * bulk-fetches history for every returned invoice, so no extra query is
     * incurred there).
     */
    private CorrectionState computeCorrectionState(
            List<InvoiceApprovalHistory> historyAscendingByActionAt
    ) {

        LocalDateTime latestRejectionAt = null;
        LocalDateTime latestCorrectionAt = null;

        for (
                InvoiceApprovalHistory entry
                : historyAscendingByActionAt
        ) {

            if (entry.getAction()
                    == InvoiceApprovalAction.REJECTED) {
                latestRejectionAt = entry.getActionAt();
            } else if (entry.getAction()
                    == InvoiceApprovalAction.CORRECTED) {
                latestCorrectionAt = entry.getActionAt();
            }
        }

        boolean correctionRequired =
                latestRejectionAt != null
                        && (latestCorrectionAt == null
                        || latestCorrectionAt.isBefore(
                                latestRejectionAt
                        ));

        return new CorrectionState(
                correctionRequired,
                latestCorrectionAt
        );
    }

    /**
     * {@code lastCorrectedAt} is the most recent {@code CORRECTED} entry's
     * {@code actionAt} regardless of cycle - purely informational - while
     * {@code correctionRequired} is the authoritative, cycle-aware
     * readiness flag actually enforced by {@link #submitForApproval(UUID)}.
     */
    private record CorrectionState(
            boolean correctionRequired,
            LocalDateTime lastCorrectedAt
    ) {
    }
}
