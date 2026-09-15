package com.AccountReceivableManagement.dto.invoice_generation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Request body of {@code PATCH /api/v1/invoices/{invoiceId}/non-financial-correction}.
 * Deliberately carries only the two Phase 2C-approved editable fields -
 * {@code clientName} and {@code projectName} - and nothing financial. The
 * {@code @NotBlank}/{@code @Size} annotations document the constraint, but
 * the mandatory/length checks are enforced explicitly in
 * {@code InvoiceServiceImpl.correctNonFinancialFields} - the same manual
 * validation/exception approach already used by {@code rejectInvoice()} -
 * rather than relying on {@code @Valid} binding, since this project has no
 * {@code MethodArgumentNotValidException} handler wired to return 400.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceNonFinancialCorrectionRequestDto {

    @NotBlank(message = "Client name is required.")
    @Size(max = 255)
    private String clientName;

    @NotBlank(message = "Project name is required.")
    @Size(max = 255)
    private String projectName;
}
