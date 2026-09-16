package com.AccountReceivableManagement.dto.invoice_generation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Request body of {@code POST /api/v1/invoices/{invoiceId}/reject}. The
 * {@code @NotBlank}/{@code @Size} annotations document the constraint, but
 * the mandatory-reason check is enforced explicitly in
 * {@code InvoiceServiceImpl.rejectInvoice} - the same manual
 * validation/exception approach already used by {@code submitForApproval()}
 * and {@code approveInvoice()} - rather than relying on {@code @Valid}
 * binding, since this project has no {@code MethodArgumentNotValidException}
 * handler wired to return 400.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceRejectionRequestDto {

    @NotBlank(message = "Rejection reason is required.")
    @Size(max = 500)
    private String reason;
}
