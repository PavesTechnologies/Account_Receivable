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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    void create_noExistingProfile_createsAndReturnsProfile() {
        when(companyProfileRepository.count()).thenReturn(0L);
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

    // The AR system supports exactly one Company Profile - a second POST
    // must be rejected as a business conflict, not silently create a
    // second (active) row.
    @Test
    void create_profileAlreadyExists_throwsDuplicateResourceExceptionAndPersistsNothing() {
        when(companyProfileRepository.count()).thenReturn(1L);

        assertThatThrownBy(() -> companyProfileService.create(validRequest()))
                .isInstanceOf(GlobalExceptionHandler.DuplicateResourceException.class);

        verify(companyProfileRepository, never()).save(any());
    }

    @Test
    void getActive_noActiveProfileConfigured_throwsResourceNotFoundException() {
        when(companyProfileRepository.findFirstByIsActiveTrueOrderByCreatedAtAsc())
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

        when(companyProfileRepository.findFirstByIsActiveTrueOrderByCreatedAtAsc())
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

    @Test
    void update_existingProfile_updatesAndReturnsIt() {
        UUID companyProfileId = UUID.randomUUID();
        CompanyProfile existing = CompanyProfile.builder()
                .companyProfileId(companyProfileId)
                .legalName("Old Legal Name")
                .city("Old City")
                .isActive(true)
                .build();

        when(companyProfileRepository.findById(companyProfileId))
                .thenReturn(Optional.of(existing));
        when(companyProfileRepository.save(any(CompanyProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CompanyProfileRequestDto update = CompanyProfileRequestDto.builder()
                .legalName("Updated Legal Name")
                .city("Hyderabad")
                .gstin("36AAAAA0000A1Z5")
                .email("updated@example.com")
                .build();

        CompanyProfileResponseDto response = companyProfileService.update(companyProfileId, update);

        assertThat(response.getCompanyProfileId()).isEqualTo(companyProfileId);
        assertThat(response.getLegalName()).isEqualTo("Updated Legal Name");
        assertThat(response.getCity()).isEqualTo("Hyderabad");
        assertThat(response.getEmail()).isEqualTo("updated@example.com");
        // isActive is not exposed on the request DTO - unaffected by an edit.
        assertThat(response.getIsActive()).isTrue();
    }
}
