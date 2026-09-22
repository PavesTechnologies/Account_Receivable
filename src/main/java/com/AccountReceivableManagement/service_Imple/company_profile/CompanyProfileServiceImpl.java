package com.AccountReceivableManagement.service_Imple.company_profile;

import com.AccountReceivableManagement.dto.company_profile.CompanyProfileRequestDto;
import com.AccountReceivableManagement.dto.company_profile.CompanyProfileResponseDto;
import com.AccountReceivableManagement.entity.company_profile.CompanyProfile;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.company_profile.CompanyProfileRepository;
import com.AccountReceivableManagement.service_interface.company_profile.CompanyProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CompanyProfileServiceImpl implements CompanyProfileService {

    private final CompanyProfileRepository companyProfileRepository;

    @Override
    public CompanyProfileResponseDto create(CompanyProfileRequestDto request) {

        CompanyProfile companyProfile =
                CompanyProfile.builder()
                        .legalName(request.getLegalName().trim())
                        .addressLine1(request.getAddressLine1())
                        .addressLine2(request.getAddressLine2())
                        .city(request.getCity())
                        .state(request.getState())
                        .postalCode(request.getPostalCode())
                        .country(request.getCountry())
                        .gstin(request.getGstin())
                        .email(request.getEmail())
                        .phone(request.getPhone())
                        .logoReference(request.getLogoReference())
                        .isActive(true)
                        .build();

        return mapToResponse(companyProfileRepository.save(companyProfile));
    }

    @Override
    public CompanyProfileResponseDto update(
            UUID companyProfileId,
            CompanyProfileRequestDto request
    ) {

        CompanyProfile companyProfile =
                companyProfileRepository.findById(companyProfileId)
                        .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                                "Company profile could not be found."
                        ));

        companyProfile.setLegalName(request.getLegalName().trim());
        companyProfile.setAddressLine1(request.getAddressLine1());
        companyProfile.setAddressLine2(request.getAddressLine2());
        companyProfile.setCity(request.getCity());
        companyProfile.setState(request.getState());
        companyProfile.setPostalCode(request.getPostalCode());
        companyProfile.setCountry(request.getCountry());
        companyProfile.setGstin(request.getGstin());
        companyProfile.setEmail(request.getEmail());
        companyProfile.setPhone(request.getPhone());
        companyProfile.setLogoReference(request.getLogoReference());

        return mapToResponse(companyProfileRepository.save(companyProfile));
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyProfileResponseDto getById(UUID companyProfileId) {

        CompanyProfile companyProfile =
                companyProfileRepository.findById(companyProfileId)
                        .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                                "Company profile could not be found."
                        ));

        return mapToResponse(companyProfile);
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyProfileResponseDto getActive() {

        CompanyProfile companyProfile =
                companyProfileRepository.findFirstByIsActiveTrue()
                        .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                                "No active company profile has been configured."
                        ));

        return mapToResponse(companyProfile);
    }

    private CompanyProfileResponseDto mapToResponse(CompanyProfile companyProfile) {

        return CompanyProfileResponseDto.builder()
                .companyProfileId(companyProfile.getCompanyProfileId())
                .legalName(companyProfile.getLegalName())
                .addressLine1(companyProfile.getAddressLine1())
                .addressLine2(companyProfile.getAddressLine2())
                .city(companyProfile.getCity())
                .state(companyProfile.getState())
                .postalCode(companyProfile.getPostalCode())
                .country(companyProfile.getCountry())
                .gstin(companyProfile.getGstin())
                .email(companyProfile.getEmail())
                .phone(companyProfile.getPhone())
                .logoReference(companyProfile.getLogoReference())
                .isActive(companyProfile.getIsActive())
                .createdAt(companyProfile.getCreatedAt())
                .updatedAt(companyProfile.getUpdatedAt())
                .build();
    }
}
