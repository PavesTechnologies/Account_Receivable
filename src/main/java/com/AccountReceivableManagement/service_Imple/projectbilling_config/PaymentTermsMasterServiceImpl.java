package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.PaymentTermsRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.PaymentTermsResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.PaymentTermsMaster;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.PaymentTermsMasterRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.PaymentTermsMasterService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.errors.DuplicateResourceException;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentTermsMasterServiceImpl implements PaymentTermsMasterService {

    private final PaymentTermsMasterRepository paymentTermsRepository;

    @Override
    public PaymentTermsResponseDto createPaymentTerm(PaymentTermsRequestDto request) {

        if (paymentTermsRepository.existsByPaymentTermNameIgnoreCase(request.getPaymentTermName())) {
            throw new DuplicateResourceException("Payment Term already exists.");
        }

        PaymentTermsMaster paymentTerm = PaymentTermsMaster.builder()
                .paymentTermName(request.getPaymentTermName().trim())
                .paymentDays(request.getPaymentDays())
                .description(request.getDescription())
                .isActive(true)
                .build();

        PaymentTermsMaster saved = paymentTermsRepository.save(paymentTerm);

        return mapToResponse(saved);
    }

    @Override
    public PaymentTermsResponseDto updatePaymentTerm(UUID paymentTermId,
                                                     PaymentTermsRequestDto request) {

        PaymentTermsMaster paymentTerm = paymentTermsRepository.findById(paymentTermId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Payment Term not found."));

        if (!paymentTerm.getPaymentTermName().equalsIgnoreCase(request.getPaymentTermName())
                && paymentTermsRepository.existsByPaymentTermNameIgnoreCase(request.getPaymentTermName())) {

            throw new DuplicateResourceException("Payment Term already exists.");
        }

        paymentTerm.setPaymentTermName(request.getPaymentTermName().trim());
        paymentTerm.setPaymentDays(request.getPaymentDays());
        paymentTerm.setDescription(request.getDescription());

        PaymentTermsMaster updated = paymentTermsRepository.save(paymentTerm);

        return mapToResponse(updated);
    }

    @Override
    public PaymentTermsResponseDto getPaymentTermById(UUID paymentTermId) {

        PaymentTermsMaster paymentTerm = paymentTermsRepository.findById(paymentTermId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Payment Term not found."));

        return mapToResponse(paymentTerm);
    }

    @Override
    public List<PaymentTermsResponseDto> getAllPaymentTerms() {

        return paymentTermsRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<PaymentTermsResponseDto> getActivePaymentTerms() {

        return paymentTermsRepository.findByIsActiveTrueOrderByPaymentDaysAsc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deletePaymentTerm(UUID paymentTermId) {

        PaymentTermsMaster paymentTerm = paymentTermsRepository.findById(paymentTermId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Payment Term not found."));

        paymentTerm.setIsActive(false);

        paymentTermsRepository.save(paymentTerm);
    }

    private PaymentTermsResponseDto mapToResponse(PaymentTermsMaster paymentTerm) {

        return PaymentTermsResponseDto.builder()
                .paymentTermId(paymentTerm.getPaymentTermId())
                .paymentTermName(paymentTerm.getPaymentTermName())
                .paymentDays(paymentTerm.getPaymentDays())
                .description(paymentTerm.getDescription())
                .isActive(paymentTerm.getIsActive())
                .createdAt(paymentTerm.getCreatedAt())
                .updatedAt(paymentTerm.getUpdatedAt())
                .build();
    }
}
