package com.AccountReceivableManagement.dto.company_profile;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceContentDefaultsResponseDto {

    private UUID companyProfileId;

    private String invoiceNotes;

    private String termsAndConditions;

    private String paymentInstructions;

    private LocalDateTime updatedAt;
}
