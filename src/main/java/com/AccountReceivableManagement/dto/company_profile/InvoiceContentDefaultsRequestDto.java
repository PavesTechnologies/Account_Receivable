package com.AccountReceivableManagement.dto.company_profile;

import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Full replacement of the three invoice-content defaults: a null or blank
 * value clears that default.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceContentDefaultsRequestDto {

    @Size(max = 5000, message = "Invoice notes must not exceed 5000 characters.")
    private String invoiceNotes;

    @Size(max = 10000, message = "Terms and conditions must not exceed 10000 characters.")
    private String termsAndConditions;

    @Size(max = 5000, message = "Payment instructions must not exceed 5000 characters.")
    private String paymentInstructions;
}
