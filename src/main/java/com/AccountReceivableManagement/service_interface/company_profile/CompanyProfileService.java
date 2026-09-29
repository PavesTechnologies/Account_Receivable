package com.AccountReceivableManagement.service_interface.company_profile;

import com.AccountReceivableManagement.dto.company_profile.CompanyProfileRequestDto;
import com.AccountReceivableManagement.dto.company_profile.CompanyProfileResponseDto;

import java.util.UUID;

public interface CompanyProfileService {

    CompanyProfileResponseDto create(CompanyProfileRequestDto request);

    CompanyProfileResponseDto update(UUID companyProfileId, CompanyProfileRequestDto request);

    CompanyProfileResponseDto getById(UUID companyProfileId);

    /**
     * The single currently-active seller/company profile, for invoice
     * generation and Invoice Preview. Throws when none has been created
     * yet - never fabricates a placeholder company.
     */
    CompanyProfileResponseDto getActive();
}
