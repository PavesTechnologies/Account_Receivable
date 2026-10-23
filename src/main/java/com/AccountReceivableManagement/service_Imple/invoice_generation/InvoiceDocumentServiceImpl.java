package com.AccountReceivableManagement.service_Imple.invoice_generation;

import com.AccountReceivableManagement.dto.invoice_generation.InvoiceItemResponseDto;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceTaxComponentResponseDto;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceDocumentService;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.AccountReceivableManagement.service_interface.company_profile.CompanySignatureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders an {@link InvoiceResponseDto} - already-persisted, authoritative
 * invoice data - into a PDF document using OpenPDF. This class performs no
 * calculation of its own: every amount printed is read directly from the
 * DTO fields exactly as returned by {@code InvoiceServiceImpl}.
 */
@Service
public class InvoiceDocumentServiceImpl implements InvoiceDocumentService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final Font TITLE_FONT =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);

    private static final Font SECTION_FONT =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);

    private static final Font BODY_FONT =
            FontFactory.getFont(FontFactory.HELVETICA, 10);

    private static final Font TABLE_HEADER_FONT =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);

    private static final Font TABLE_BODY_FONT =
            FontFactory.getFont(FontFactory.HELVETICA, 9);

    private static final Color HEADER_BACKGROUND = new Color(51, 51, 51);

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(InvoiceDocumentServiceImpl.class);

    /** Null when constructed without Spring (e.g. layout-only tests): no signature is rendered. */
    private final CompanySignatureService signatureService;

    public InvoiceDocumentServiceImpl() {
        this.signatureService = null;
    }

    @Autowired
    public InvoiceDocumentServiceImpl(CompanySignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @Override
    public byte[] generateInvoicePdf(InvoiceResponseDto invoice) {

        Document document = new Document(PageSize.A4, 40, 40, 50, 40);

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, outputStream);

            document.open();

            document.add(new Paragraph("INVOICE", TITLE_FONT));
            document.add(Chunk.NEWLINE);

            addSellerAndBuyer(document, invoice);
            document.add(Chunk.NEWLINE);

            addInvoiceMeta(document, invoice);
            document.add(Chunk.NEWLINE);

            addItemsTable(document, invoice.getItems());
            document.add(Chunk.NEWLINE);

            addTaxTable(document, invoice.getTaxComponents());
            document.add(Chunk.NEWLINE);

            addTotals(document, invoice);

            addTextSection(document, "Notes", invoice.getInvoiceNotes());
            addTextSection(document, "Payment Instructions", invoice.getPaymentInstructions());
            addTextSection(document, "Terms & Conditions", invoice.getTermsAndConditions());

            addAuthorizedSignatory(document, invoice);

            document.close();

            return outputStream.toByteArray();

        } catch (DocumentException ex) {

            throw new PdfGenerationException(
                    "Failed to generate the invoice PDF.",
                    ex
            );

        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
    }

    /**
     * Prints the invoice's own frozen signature image above a plain
     * "Authorized Signatory" caption. Nothing is printed when the invoice has
     * no signature, or the stored asset is missing or unreadable - in which
     * case the PDF is still produced. No claim of digital signing or approval
     * is made.
     */
    private void addAuthorizedSignatory(Document document, InvoiceResponseDto invoice) throws DocumentException {
        String reference = invoice.getSellerSignatureReference();
        if (reference == null || reference.isBlank() || signatureService == null) {
            return;
        }
        Image image;
        try {
            image = signatureService.findImage(reference)
                    .map(stored -> {
                        try {
                            return Image.getInstance(stored.content());
                        } catch (Exception ex) {
                            return null;
                        }
                    })
                    .orElse(null);
        } catch (RuntimeException ex) {
            image = null;
        }
        if (image == null) {
            log.warn("[InvoicePdf] signature asset {} for invoice {} is unavailable; rendering without it.",
                    reference, invoice.getInvoiceNumber());
            return;
        }
        image.scaleToFit(150, 60);
        image.setAlignment(Image.LEFT);
        document.add(Chunk.NEWLINE);
        document.add(image);
        document.add(new Paragraph("Authorized Signatory", BODY_FONT));
    }

    private void addSellerAndBuyer(
            Document document,
            InvoiceResponseDto invoice
    ) throws DocumentException {

        document.add(new Paragraph("From", SECTION_FONT));
        addLineIfPresent(document, invoice.getSellerLegalName());
        addLineIfPresent(document, invoice.getSellerAddressLine1());
        addLineIfPresent(document, invoice.getSellerAddressLine2());
        addLineIfPresent(document, joinNonBlank(
                invoice.getSellerCity(),
                invoice.getSellerState(),
                invoice.getSellerPostalCode(),
                invoice.getSellerCountry()
        ));
        addLineIfPresent(document, prefixIfPresent("GSTIN: ", invoice.getSellerGstin()));
        addLineIfPresent(document, prefixIfPresent("Email: ", invoice.getSellerEmail()));
        addLineIfPresent(document, prefixIfPresent("Phone: ", invoice.getSellerPhone()));

        document.add(Chunk.NEWLINE);

        document.add(new Paragraph("Bill To", SECTION_FONT));
        addLineIfPresent(document, invoice.getClientName());
        addLineIfPresent(document, invoice.getBillingAddress());
        addLineIfPresent(document, prefixIfPresent("GSTIN: ", invoice.getGstinOrTaxId()));
        addLineIfPresent(document, prefixIfPresent("Email: ", invoice.getEmail()));
        addLineIfPresent(document, prefixIfPresent("Phone: ", invoice.getPhone()));
    }

    private void addInvoiceMeta(
            Document document,
            InvoiceResponseDto invoice
    ) throws DocumentException {

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 1f});

        addMetaRow(table, "Invoice Number", invoice.getInvoiceNumber());
        addMetaRow(table, "Invoice Date", formatDate(invoice.getInvoiceDate()));
        addMetaRow(table, "Due Date", formatDate(invoice.getDueDate()));
        addMetaRow(
                table,
                "Billing Period",
                formatDate(invoice.getBillingPeriodStart())
                        + " - "
                        + formatDate(invoice.getBillingPeriodEnd())
        );
        addMetaRow(table, "Currency", nullToDash(invoice.getCurrencyCode()));
        addMetaRow(
                table,
                "Payment Terms",
                invoice.getPaymentTermName() != null
                        ? invoice.getPaymentTermName()
                        : nullToDash(invoice.getPaymentTermCode())
        );

        document.add(table);
    }

    private void addMetaRow(PdfPTable table, String label, String value) {

        PdfPCell labelCell = new PdfPCell(new Phrase(label, TABLE_BODY_FONT));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(3f);

        PdfPCell valueCell = new PdfPCell(new Phrase(nullToDash(value), TABLE_BODY_FONT));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(3f);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    /**
     * Lines are grouped the way the invoice preview shows them: time & material services, expenses (never carrying a
     * T&M discount) and any other charge type, each in its own table. Every figure is the persisted line value.
     */
    private void addItemsTable(
            Document document,
            List<InvoiceItemResponseDto> items
    ) throws DocumentException {

        List<InvoiceItemResponseDto> all = items != null ? items : List.of();

        if (all.isEmpty()) {
            document.add(new Paragraph("Invoice Items", SECTION_FONT));
            document.add(new Paragraph("No billable line items on this invoice.", BODY_FONT));
            return;
        }

        List<InvoiceItemResponseDto> timeLines = linesOfType(all, BillingItemType.TIME_ENTRY);
        List<InvoiceItemResponseDto> expenseLines = linesOfType(all, BillingItemType.EXPENSE);
        List<InvoiceItemResponseDto> otherLines = all.stream()
                .filter(item -> item.getItemType() != BillingItemType.TIME_ENTRY
                        && item.getItemType() != BillingItemType.EXPENSE)
                .toList();

        boolean first = true;
        if (!timeLines.isEmpty()) {
            addTimeLinesTable(document, timeLines);
            first = false;
        }
        if (!expenseLines.isEmpty()) {
            if (!first) {
                document.add(Chunk.NEWLINE);
            }
            addExpenseLinesTable(document, expenseLines);
            first = false;
        }
        if (!otherLines.isEmpty()) {
            if (!first) {
                document.add(Chunk.NEWLINE);
            }
            addOtherLinesTable(document, otherLines);
        }
    }

    private static List<InvoiceItemResponseDto> linesOfType(List<InvoiceItemResponseDto> items, BillingItemType type) {
        return items.stream().filter(item -> item.getItemType() == type).toList();
    }

    private static BigDecimal sumOfAmounts(List<InvoiceItemResponseDto> lines) {
        return lines.stream()
                .map(InvoiceItemResponseDto::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void addTimeLinesTable(Document document, List<InvoiceItemResponseDto> lines) throws DocumentException {
        document.add(new Paragraph("Professional Services (Time & Material)", SECTION_FONT));

        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.1f, 2.4f, 0.8f, 1f, 1.6f, 1f, 1.1f});
        table.setHeaderRows(1);

        addHeaderCell(table, "Date");
        addHeaderCell(table, "Description");
        addHeaderCell(table, "Hours");
        addHeaderCell(table, "Base Rate");
        addHeaderCell(table, "Discount");
        addHeaderCell(table, "Net Rate");
        addHeaderCell(table, "Amount");

        for (InvoiceItemResponseDto item : lines) {
            String name = item.getResourceName() != null && !item.getResourceName().isBlank()
                    ? item.getResourceName()
                    : nullToDash(item.getItemName());
            String description = item.getRole() != null && !item.getRole().isBlank()
                    ? name + "\n" + item.getRole()
                    : name;

            addBodyCell(table, formatDate(item.getWorkDate()));
            addBodyCell(table, description);
            addBodyCell(table, formatAmount(item.getQuantity()));
            addBodyCell(table, item.getBaseRate() != null ? formatAmount(item.getBaseRate()) : "-");
            addBodyCell(table, describeDiscount(item));
            addBodyCell(table, formatAmount(item.getRate()));
            addBodyCell(table, formatAmount(item.getAmount()));
        }

        document.add(table);
    }

    private void addExpenseLinesTable(Document document, List<InvoiceItemResponseDto> lines) throws DocumentException {
        document.add(new Paragraph("Expenses", SECTION_FONT));

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.1f, 4f, 0.8f, 1.3f});
        table.setHeaderRows(1);

        addHeaderCell(table, "Date");
        addHeaderCell(table, "Expense");
        addHeaderCell(table, "Qty");
        addHeaderCell(table, "Amount");

        for (InvoiceItemResponseDto item : lines) {
            addBodyCell(table, formatDate(item.getWorkDate()));
            addBodyCell(table, nullToDash(item.getItemName()));
            addBodyCell(table, formatAmount(item.getQuantity()));
            addBodyCell(table, formatAmount(item.getAmount()));
        }

        document.add(table);
    }

    private void addOtherLinesTable(Document document, List<InvoiceItemResponseDto> lines) throws DocumentException {
        document.add(new Paragraph("Invoice Items", SECTION_FONT));

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{4f, 0.8f, 1.2f, 1.3f});
        table.setHeaderRows(1);

        addHeaderCell(table, "Description");
        addHeaderCell(table, "Qty");
        addHeaderCell(table, "Rate");
        addHeaderCell(table, "Amount");

        for (InvoiceItemResponseDto item : lines) {
            addBodyCell(table, nullToDash(item.getItemName()));
            addBodyCell(table, formatAmount(item.getQuantity()));
            addBodyCell(table, formatAmount(item.getRate()));
            addBodyCell(table, formatAmount(item.getAmount()));
        }

        document.add(table);
    }

    /** "10%" / "20.00 off" plus the period name; "-" when no discount applied (always so for expenses). */
    static String describeDiscount(InvoiceItemResponseDto item) {
        if (item.getDiscountValue() == null
                || item.getDiscountValue().signum() <= 0
                || item.getDiscountType() == null) {
            return "-";
        }
        String value = "PERCENTAGE".equalsIgnoreCase(item.getDiscountType())
                ? item.getDiscountValue().stripTrailingZeros().toPlainString() + "%"
                : item.getDiscountValue().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + " off";
        String period = item.getDiscountPeriodName();
        return period != null && !period.isBlank() ? value + " (" + period + ")" : value;
    }

    private void addTaxTable(
            Document document,
            List<InvoiceTaxComponentResponseDto> taxComponents
    ) throws DocumentException {

        document.add(new Paragraph("Tax", SECTION_FONT));

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2f, 1f, 1.5f, 1.5f});

        addHeaderCell(table, "Tax Type");
        addHeaderCell(table, "Rate");
        addHeaderCell(table, "Applicability");
        addHeaderCell(table, "Amount");

        if (taxComponents != null) {
            for (InvoiceTaxComponentResponseDto component : taxComponents) {

                String taxTypeLabel =
                        component.getTaxTypeName() != null
                                ? component.getTaxTypeName()
                                : nullToDash(component.getTaxTypeCode());

                addBodyCell(table, taxTypeLabel);
                addBodyCell(
                        table,
                        component.getAppliedRate() != null
                                ? component.getAppliedRate() + "%"
                                : "-"
                );
                addBodyCell(
                        table,
                        component.getApplicabilityType() != null
                                ? component.getApplicabilityType().name()
                                : "-"
                );
                addBodyCell(table, formatAmount(component.getTaxAmount()));
            }
        }

        document.add(table);
    }

    private void addTotals(
            Document document,
            InvoiceResponseDto invoice
    ) throws DocumentException {

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(65);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setWidths(new float[]{1.7f, 1f});

        List<InvoiceItemResponseDto> items = invoice.getItems() != null ? invoice.getItems() : List.of();
        List<InvoiceItemResponseDto> timeLines = linesOfType(items, BillingItemType.TIME_ENTRY);
        List<InvoiceItemResponseDto> expenseLines = linesOfType(items, BillingItemType.EXPENSE);
        if (!timeLines.isEmpty() && !expenseLines.isEmpty()) {
            // Sums of the persisted line amounts shown above; the subtotal below stays the backend's figure.
            addTotalRow(table, "Time & Material Subtotal", invoice.getTimeMaterialSubtotal() != null ? invoice.getTimeMaterialSubtotal() : sumOfAmounts(timeLines), invoice.getCurrencyCode());
            addTotalRow(table, "Expenses Subtotal", invoice.getExpenseSubtotal() != null ? invoice.getExpenseSubtotal() : sumOfAmounts(expenseLines), invoice.getCurrencyCode());
        }
        addTotalRow(table, "Taxable Subtotal", invoice.getSubtotal(), invoice.getCurrencyCode());
        addTotalRow(table, "Total Tax", invoice.getTotalTaxAmount(), invoice.getCurrencyCode());
        addTotalRow(table, "Total Invoice Amount", invoice.getGrandTotal(), invoice.getCurrencyCode());

        document.add(table);
    }

    private void addTotalRow(
            PdfPTable table,
            String label,
            BigDecimal value,
            String currencyCode
    ) {

        PdfPCell labelCell = new PdfPCell(new Phrase(label, SECTION_FONT));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(3f);

        PdfPCell valueCell = new PdfPCell(
                new Phrase(formatCurrency(value, currencyCode), SECTION_FONT)
        );
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setPadding(3f);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TABLE_HEADER_FONT));
        cell.setBackgroundColor(HEADER_BACKGROUND);
        cell.setPadding(4f);
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", TABLE_BODY_FONT));
        cell.setPadding(4f);
        table.addCell(cell);
    }

    /** Printed only when the invoice carries saved content - never a placeholder. */
    private void addTextSection(
            Document document,
            String heading,
            String text
    ) throws DocumentException {

        if (text == null || text.isBlank()) {
            return;
        }

        document.add(Chunk.NEWLINE);
        document.add(new Paragraph(heading, SECTION_FONT));
        document.add(new Paragraph(text, BODY_FONT));
    }

    private void addLineIfPresent(Document document, String value) throws DocumentException {
        if (value != null && !value.isBlank()) {
            document.add(new Paragraph(value, BODY_FONT));
        }
    }

    private String joinNonBlank(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(part.trim());
            }
        }
        return builder.length() > 0 ? builder.toString() : null;
    }

    private String prefixIfPresent(String prefix, String value) {
        return value != null && !value.isBlank() ? prefix + value : null;
    }

    private String formatDate(java.time.LocalDate date) {
        return date != null ? date.format(DATE_FORMAT) : "-";
    }

    private String formatAmount(BigDecimal amount) {
        return amount != null ? amount.toPlainString() : "-";
    }

    private String formatCurrency(BigDecimal amount, String currencyCode) {
        if (amount == null) {
            return "-";
        }
        return (currencyCode != null ? currencyCode + " " : "") + amount.toPlainString();
    }

    private String nullToDash(String value) {
        return value != null && !value.isBlank() ? value : "-";
    }
}
