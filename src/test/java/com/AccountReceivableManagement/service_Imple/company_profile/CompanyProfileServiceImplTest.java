package com.AccountReceivableManagement.service_Imple.company_profile;

import com.AccountReceivableManagement.dto.company_profile.CompanyProfileRequestDto;
import com.AccountReceivableManagement.dto.company_profile.CompanyProfileResponseDto;
import com.AccountReceivableManagement.entity.company_profile.CompanyProfile;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.company_profile.CompanyProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyProfileServiceImplTest {

    @Mock
    private CompanyProfileRepository companyProfileRepository;

    @InjectMocks
    private CompanyProfileServiceImpl companyProfileService;

    private CompanyProfileRequestDto validRequest() {
        return CompanyProfileRequestDto.builder()
                .legalName("Example Global Infotech Private Limited")
                .addressLine1("Tower B, Tech Park")
                .city("Hyderabad")
                .state("Telangana")
                .postalCode("500081")
                .country("India")
                .gstin("36AAAAA0000A1Z5")
                .email("billing@example.com")
                .phone("+91 40 1234 5678")
                .build();
    }

    @Test
    void create_validRequest_createsAndReturnsProfile() {
        when(companyProfileRepository.save(any(CompanyProfile.class)))
                .thenAnswer(invocation -> {
                    CompanyProfile saved = invocation.getArgument(0);
                    saved.setCompanyProfileId(UUID.randomUUID());
                    return saved;
                });

        CompanyProfileResponseDto response = companyProfileService.create(validRequest());

        assertThat(response.getCompanyProfileId()).isNotNull();
        assertThat(response.getLegalName()).isEqualTo("Example Global Infotech Private Limited");
        assertThat(response.getGstin()).isEqualTo("36AAAAA0000A1Z5");
        assertThat(response.getIsActive()).isTrue();
    }

    @Test
    void getActive_noActiveProfileConfigured_throwsResourceNotFoundException() {
        when(companyProfileRepository.findFirstByIsActiveTrue())
                .thenReturn(Optional.empty());

        assertThatThrownBy(companyProfileService::getActive)
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class);
    }

    @Test
    void getActive_activeProfileConfigured_returnsIt() {
        CompanyProfile profile = CompanyProfile.builder()
                .companyProfileId(UUID.randomUUID())
                .legalName("Example Global Infotech Private Limited")
                .isActive(true)
                .build();

        when(companyProfileRepository.findFirstByIsActiveTrue())
                .thenReturn(Optional.of(profile));

        CompanyProfileResponseDto response = companyProfileService.getActive();

        assertThat(response.getLegalName()).isEqualTo("Example Global Infotech Private Limited");
    }

    @Test
    void update_nonexistentProfile_throwsResourceNotFoundException() {
        UUID companyProfileId = UUID.randomUUID();
        when(companyProfileRepository.findById(companyProfileId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyProfileService.update(companyProfileId, validRequest()))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class);
    }
}
