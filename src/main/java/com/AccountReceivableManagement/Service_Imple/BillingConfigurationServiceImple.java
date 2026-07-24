//package com.AccountReceivableManagement.Service_Imple;
//
//import com.AccountReceivableManagement.DTO.project_billing_config.BillingConfigurationRequestDto;
//import com.AccountReceivableManagement.DTO.project_billing_config.BillingConfigurationResponseDto;
//import com.AccountReceivableManagement.Repo.billing_config.BillingConfigurationRepository;
//import com.AccountReceivableManagement.Repo.client.ClientRepository;
//import com.AccountReceivableManagement.Service_Interface.BillingConfigurationService;
//import jakarta.transaction.Transactional;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//
//@Service
//@RequiredArgsConstructor
//public class BillingConfigurationServiceImple implements BillingConfigurationService {
//    private final BillingConfigurationRepository billingConfigurationRepository;
//
//    // Inject these repositories from RMS/PMS
//    private final ClientRepository clientRepository;
////    private final ProjectRepository projectRepository;
//
//    @Override
//    @Transactional
//    public BillingConfigurationResponseDto createBillingConfiguration(
//            BillingConfigurationRequestDto requestDto) {
//
//        /*
//         * ============================================
//         * STEP 1 : Validate Client
//         * ============================================
//         */
//
//        Client client = clientRepository.findById(requestDto.getClientId())
//                .orElseThrow(() ->
//                        new RuntimeException("Client not found"));
//
//        if (!client.getActiveFlag()) {
//            throw new RuntimeException("Client is inactive.");
//        }
//
//        /*
//         * ============================================
//         * STEP 2 : Validate Project
//         * ============================================
//         */
//
//        Project project = projectRepository.findById(requestDto.getProjectId())
//                .orElseThrow(() ->
//                        new RuntimeException("Project not found"));
//
//        if (!project.getActiveFlag()) {
//            throw new RuntimeException("Project is inactive.");
//        }
//
//        /*
//         * ============================================
//         * STEP 3 : Validate Project belongs to Client
//         * ============================================
//         */
//
//        if (!project.getClientId().equals(client.getClientId())) {
//
//            throw new RuntimeException(
//                    "Selected project does not belong to selected client.");
//
//        }
//
//        /*
//         * ============================================
//         * STEP 4 : Duplicate Active Configuration
//         * ============================================
//         */
//
//        boolean alreadyExists =
//                billingConfigurationRepository
//                        .existsByProjectIdAndStatus(
//                                requestDto.getProjectId(),
//                                BillingConfigurationStatus.ACTIVE);
//
//        if (alreadyExists) {
//
//            throw new DuplicateBillingConfigurationException(
//                    "Active Billing Configuration already exists.");
//
//        }
//
//        /*
//         * ============================================
//         * STEP 5 : Effective Date Validation
//         * ============================================
//         */
//
//        if (requestDto.getEffectiveTo() != null &&
//                requestDto.getEffectiveTo()
//                        .isBefore(requestDto.getEffectiveFrom())) {
//
//            throw new RuntimeException(
//                    "Effective To Date cannot be before Effective From Date.");
//
//        }
//
//        /*
//         * ============================================
//         * STEP 6 : Entity Mapping
//         * ============================================
//         */
//
//        BillingConfiguration entity = new BillingConfiguration();
//
//        entity.setClientId(requestDto.getClientId());
//
//        entity.setProjectId(requestDto.getProjectId());
//
//        entity.setEffectiveFrom(requestDto.getEffectiveFrom());
//
//        entity.setEffectiveTo(requestDto.getEffectiveTo());
//
//        entity.setStatus(BillingConfigurationStatus.DRAFT);
//
//        entity.setVersionNo(1);
//
//        entity.setActiveFlag(Boolean.TRUE);
//
//        entity.setCreatedDate(LocalDateTime.now());
//
//        // Replace with Logged-in User
//        entity.setCreatedBy("Finance Executive");
//
//        /*
//         * ============================================
//         * STEP 7 : Save
//         * ============================================
//         */
//
//        BillingConfiguration saved =
//                billingConfigurationRepository.save(entity);
//
//        /*
//         * ============================================
//         * STEP 8 : Response Mapping
//         * ============================================
//         */
//
//        BillingConfigurationResponseDto response =
//                new BillingConfigurationResponseDto();
//
//        response.setBillingConfigurationId(
//                saved.getBillingConfigurationId());
//
//        response.setClientId(saved.getClientId());
//
//        response.setProjectId(saved.getProjectId());
//
//        response.setStatus(saved.getStatus());
//
//        response.setEffectiveFrom(saved.getEffectiveFrom());
//
//        response.setEffectiveTo(saved.getEffectiveTo());
//
//        response.setVersionNo(saved.getVersionNo());
//
//        response.setCreatedBy(saved.getCreatedBy());
//
//        response.setCreatedDate(saved.getCreatedDate());
//
//        return response;
//    }
//
//
//}
