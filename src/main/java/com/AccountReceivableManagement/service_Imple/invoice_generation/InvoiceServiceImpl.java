package com.AccountReceivableManagement.service_Imple.invoice_generation;

import com.AccountReceivableManagement.dto.invoice_generation.InvoiceApprovalHistoryResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceApprovalSummaryResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceApprovalWorkspaceResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceItemResponseDto;
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
import com.AccountReceivableManagement.entity.projectbilling_config.PaymentTermsMaster;
import com.AccountReceivableManagement.entity.tax_calculation.TaxCalculation;
import com.AccountReceivableManagement.entity.tax_calculation.TaxCalculationComponent;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceApprovalAction;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceApprovalHistoryRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.PaymentTermsMasterRepository;
import com.AccountReceivableManagement.repo.tax_calculation.TaxCalculationRepository;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingConfigurationService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
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

    private final InvoiceRepository invoiceRepository;

    private final InvoiceApprovalHistoryRepository invoiceApprovalHistoryRepository;

    private final BillingSnapshotRepository billingSnapshotRepository;

    private final TaxCalculationRepository taxCalculationRepository;

    private final BillingConfigurationService billingConfigurationService;

    private final PaymentTermsMasterRepository paymentTermsMasterRepository;

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
        validateTaxCalculationConsistency(taxCalculation);

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

        if (invoice.getStatus()
                != InvoiceStatus.GENERATED) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Only invoices with status GENERATED can be submitted for approval."
            );
        }

        InvoiceStatus previousStatus = invoice.getStatus();

        invoice.setStatus(InvoiceStatus.PENDING_APPROVAL);

        Invoice saved = invoiceRepository.save(invoice);

        recordHistory(
                saved.getInvoiceId(),
                previousStatus,
                InvoiceStatus.PENDING_APPROVAL,
                InvoiceApprovalAction.SUBMITTED,
                null
        );

        return mapToResponse(saved);
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
     * generation if the persisted TaxCalculation's own totals are internally
     * inconsistent, rather than silently correcting them.
     */
    private void validateTaxCalculationConsistency(
            TaxCalculation taxCalculation
    ) {

        BigDecimal expectedGrandTotal =
                taxCalculation.getTaxableAmount()
                        .add(taxCalculation.getTotalTaxAmount());

        if (expectedGrandTotal.compareTo(
                taxCalculation.getGrandTotal()
        ) != 0) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Tax calculation totals are inconsistent for this billing snapshot. Invoice cannot be generated."
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

        return InvoiceResponseDto.builder()
                .invoiceId(invoice.getInvoiceId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .status(invoice.getStatus())
                .billingSnapshotId(
                        invoice.getBillingSnapshotId()
                )
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
                .build();
    }
}
