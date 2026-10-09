package com.AccountReceivableManagement.dto.company_profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProfileRequestDto {

    @NotBlank(message = "Legal name is required.")
    @Size(max = 255, message = "Legal name must not exceed 255 characters.")
    private String legalName;

    @Size(max = 255)
    private String addressLine1;

    @Size(max = 255)
    private String addressLine2;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String state;

    @Size(max = 20)
    private String postalCode;

    @Size(max = 100)
    private String country;

    @Size(max = 50)
    private String gstin;

    @Email(message = "Email must be a valid email address.")
    @Size(max = 255)
    private String email;

    @Size(max = 30)
    private String phone;

    @Size(max = 500)
    private String logoReference;

    /**
     * Optional. When null the existing default is left untouched; use the
     * dedicated invoice-content-defaults endpoint to clear one.
     */
    @Size(max = 5000, message = "Invoice notes must not exceed 5000 characters.")
    private String defaultInvoiceNotes;

    @Size(max = 10000, message = "Terms and conditions must not exceed 10000 characters.")
    private String defaultTermsAndConditions;

    @Size(max = 5000, message = "Payment instructions must not exceed 5000 characters.")
    private String defaultPaymentInstructions;
}
