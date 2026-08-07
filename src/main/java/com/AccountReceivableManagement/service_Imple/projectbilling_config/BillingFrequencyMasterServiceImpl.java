package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingFrequencyRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingFrequencyResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFrequencyMaster;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingFrequencyMasterRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingFrequencyMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillingFrequencyMasterServiceImpl implements BillingFrequencyMasterService {

    private final BillingFrequencyMasterRepository billingFrequencyMasterRepository;

    @Override
    public BillingFrequencyResponseDto createBillingFrequency(BillingFrequencyRequestDto request) {

        if (billingFrequencyMasterRepository.existsByBillingFrequencyNameIgnoreCase(request.getBillingFrequencyName())) {
            throw new GlobalExceptionHandler.DuplicateResourceException("Billing Frequency already exists.");
        }

        BillingFrequencyMaster billingFrequency = BillingFrequencyMaster.builder()
                .billingFrequencyName(request.getBillingFrequencyName().trim())
                .description(request.getDescription())
                .isActive(true)
                .build();

        BillingFrequencyMaster saved = billingFrequencyMasterRepository.save(billingFrequency);

        return mapToResponse(saved);
    }

    @Override
    public BillingFrequencyResponseDto updateBillingFrequency(UUID billingFrequencyId,
                                                              BillingFrequencyRequestDto request) {

        BillingFrequencyMaster billingFrequency = billingFrequencyMasterRepository.findById(billingFrequencyId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Billing Frequency not found."));

        if (!billingFrequency.getBillingFrequencyName().equalsIgnoreCase(request.getBillingFrequencyName())
                && billingFrequencyMasterRepository.existsByBillingFrequencyNameIgnoreCase(request.getBillingFrequencyName())) {

            throw new GlobalExceptionHandler.DuplicateResourceException("Billing Frequency already exists.");
        }

        billingFrequency.setBillingFrequencyName(request.getBillingFrequencyName().trim());
        billingFrequency.setDescription(request.getDescription());

        BillingFrequencyMaster updated = billingFrequencyMasterRepository.save(billingFrequency);

        return mapToResponse(updated);
    }

    @Override
    public BillingFrequencyResponseDto getBillingFrequencyById(UUID billingFrequencyId) {

        BillingFrequencyMaster billingFrequency = billingFrequencyMasterRepository.findById(billingFrequencyId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Billing Frequency not found."));

        return mapToResponse(billingFrequency);
    }

    @Override
    public List<BillingFrequencyResponseDto> getAllBillingFrequencies() {

        return billingFrequencyMasterRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<BillingFrequencyResponseDto> getActiveBillingFrequencies() {

        return billingFrequencyMasterRepository.findByIsActiveTrueOrderByBillingFrequencyNameAsc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteBillingFrequency(UUID billingFrequencyId) {

        BillingFrequencyMaster billingFrequency = billingFrequencyMasterRepository.findById(billingFrequencyId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Billing Frequency not found."));

        billingFrequency.setIsActive(false);

        billingFrequencyMasterRepository.save(billingFrequency);
    }

    private BillingFrequencyResponseDto mapToResponse(BillingFrequencyMaster billingFrequency) {

        return BillingFrequencyResponseDto.builder()
                .billingFrequencyId(billingFrequency.getBillingFrequencyId())
                .billingFrequencyName(billingFrequency.getBillingFrequencyName())
                .description(billingFrequency.getDescription())
                .isActive(billingFrequency.getIsActive())
                .createdAt(billingFrequency.getCreatedAt())
                .updatedAt(billingFrequency.getUpdatedAt())
                .build();
    }
}
