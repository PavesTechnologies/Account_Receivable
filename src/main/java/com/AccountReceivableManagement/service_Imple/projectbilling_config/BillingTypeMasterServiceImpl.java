package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingTypeRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingTypeResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler.DuplicateResourceException;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTypeMasterRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingTypeMasterService;
import lombok.RequiredArgsConstructor;
//import org.apache.kafka.common.errors.DuplicateResourceException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillingTypeMasterServiceImpl implements BillingTypeMasterService {

    private final BillingTypeMasterRepository billingTypeRepository;

    @Override
    public BillingTypeResponseDto createBillingType(BillingTypeRequestDto request) {

        if (billingTypeRepository.existsByBillingTypeNameIgnoreCase(request.getBillingTypeName())) {
            throw new DuplicateResourceException("Billing Type already exists.");
        }

        BillingTypeMaster billingType = BillingTypeMaster.builder()
                .billingTypeName(request.getBillingTypeName().trim())
                .description(request.getDescription())
                .isActive(true)
                .build();

        BillingTypeMaster saved = billingTypeRepository.save(billingType);

        return mapToResponse(saved);
    }

    @Override
    public BillingTypeResponseDto updateBillingType(UUID billingTypeId,
                                                    BillingTypeRequestDto request) {

        BillingTypeMaster billingType = billingTypeRepository.findById(billingTypeId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Billing Type not found."));

        if (!billingType.getBillingTypeName().equalsIgnoreCase(request.getBillingTypeName())
                && billingTypeRepository.existsByBillingTypeNameIgnoreCase(request.getBillingTypeName())) {

            throw new DuplicateResourceException("Billing Type already exists.");
        }
        System.out.println("Before Update: " + billingType.getIsActive());

        billingType.setBillingTypeName(request.getBillingTypeName().trim());
        billingType.setDescription(request.getDescription());

        System.out.println("Before Save: " + billingType.getIsActive());

        BillingTypeMaster updated = billingTypeRepository.save(billingType);

        System.out.println("After Save: " + updated.getIsActive());

        return mapToResponse(updated);
    }

    @Override
    public BillingTypeResponseDto getBillingTypeById(UUID billingTypeId) {

        BillingTypeMaster billingType = billingTypeRepository.findById(billingTypeId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Billing Type not found."));

        return mapToResponse(billingType);
    }

    @Override
    public List<BillingTypeResponseDto> getAllBillingTypes() {

        return billingTypeRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<BillingTypeResponseDto> getActiveBillingTypes() {

        return billingTypeRepository.findByIsActiveTrueOrderByBillingTypeNameAsc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteBillingType(UUID billingTypeId) {

        BillingTypeMaster billingType = billingTypeRepository.findById(billingTypeId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Billing Type not found."));

        billingType.setIsActive(false);

        billingTypeRepository.save(billingType);
    }

    @Override
    public BillingTypeResponseDto activateBillingType(UUID billingTypeId) {

        BillingTypeMaster billingType = billingTypeRepository.findById(billingTypeId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Billing Type not found."));

        if (Boolean.TRUE.equals(billingType.getIsActive())) {
            throw new GlobalExceptionHandler.ValidationException("Billing Type is already active.");
        }

        billingType.setIsActive(true);

        BillingTypeMaster updated = billingTypeRepository.save(billingType);

        return mapToResponse(updated);
    }

    private BillingTypeResponseDto mapToResponse(BillingTypeMaster billingType) {

        return BillingTypeResponseDto.builder()
                .billingTypeId(billingType.getBillingTypeId())
                .billingTypeName(billingType.getBillingTypeName())
                .description(billingType.getDescription())
                .isActive(billingType.getIsActive())
                .createdAt(billingType.getCreatedAt())
                .updatedAt(billingType.getUpdatedAt())
                .build();
    }
}
