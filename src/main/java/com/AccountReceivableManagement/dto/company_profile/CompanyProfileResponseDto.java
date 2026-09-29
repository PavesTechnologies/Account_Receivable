package com.AccountReceivableManagement.dto.company_profile;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProfileResponseDto {

    private UUID companyProfileId;

    private String legalName;

    private String addressLine1;

    private String addressLine2;

    private String city;

    private String state;

    private String postalCode;

    private String country;

    private String gstin;

    private String email;

    private String phone;

    private String logoReference;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
