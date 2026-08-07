package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.ProrationRuleRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.ProrationRuleResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.ProrationRuleMaster;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.ProrationRuleMasterRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.ProrationRuleMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProrationRuleMasterServiceImpl implements ProrationRuleMasterService {

    private final ProrationRuleMasterRepository prorationRuleRepository;

    @Override
    public ProrationRuleResponseDto createProrationRule(ProrationRuleRequestDto request) {

        if (prorationRuleRepository.existsByProrationRuleCodeIgnoreCase(request.getProrationRuleCode())) {
            throw new GlobalExceptionHandler.DuplicateResourceException("Proration rule code already exists.");
        }

        if (prorationRuleRepository.existsByProrationRuleNameIgnoreCase(request.getProrationRuleName())) {
            throw new GlobalExceptionHandler.DuplicateResourceException("Proration rule name already exists.");
        }

        ProrationRuleMaster prorationRule = ProrationRuleMaster.builder()
                .prorationRuleCode(request.getProrationRuleCode().trim().toUpperCase())
                .prorationRuleName(request.getProrationRuleName().trim())
                .description(request.getDescription())
                .isActive(true)
                .build();

        ProrationRuleMaster savedProrationRule = prorationRuleRepository.save(prorationRule);

        return mapToResponse(savedProrationRule);
    }

    @Override
    public ProrationRuleResponseDto updateProrationRule(UUID prorationRuleId,
                                                         ProrationRuleRequestDto request) {

        ProrationRuleMaster prorationRule = prorationRuleRepository.findById(prorationRuleId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Proration Rule not found."));

        if (!prorationRule.getProrationRuleCode().equalsIgnoreCase(request.getProrationRuleCode())
                && prorationRuleRepository.existsByProrationRuleCodeIgnoreCase(request.getProrationRuleCode())) {

            throw new GlobalExceptionHandler.DuplicateResourceException("Proration rule code already exists.");
        }

        if (!prorationRule.getProrationRuleName().equalsIgnoreCase(request.getProrationRuleName())
                && prorationRuleRepository.existsByProrationRuleNameIgnoreCase(request.getProrationRuleName())) {

            throw new GlobalExceptionHandler.DuplicateResourceException("Proration rule name already exists.");
        }

        prorationRule.setProrationRuleCode(request.getProrationRuleCode().trim().toUpperCase());
        prorationRule.setProrationRuleName(request.getProrationRuleName().trim());
        prorationRule.setDescription(request.getDescription());

        ProrationRuleMaster updatedProrationRule = prorationRuleRepository.save(prorationRule);

        return mapToResponse(updatedProrationRule);
    }

    @Override
    public ProrationRuleResponseDto getProrationRuleById(UUID prorationRuleId) {

        ProrationRuleMaster prorationRule = prorationRuleRepository.findById(prorationRuleId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Proration Rule not found."));

        return mapToResponse(prorationRule);
    }

    @Override
    public List<ProrationRuleResponseDto> getAllProrationRules() {

        return prorationRuleRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProrationRuleResponseDto> getActiveProrationRules() {

        return prorationRuleRepository.findByIsActiveTrueOrderByProrationRuleCodeAsc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteProrationRule(UUID prorationRuleId) {

        ProrationRuleMaster prorationRule = prorationRuleRepository.findById(prorationRuleId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Proration Rule not found."));

        prorationRule.setIsActive(false);

        prorationRuleRepository.save(prorationRule);
    }

    private ProrationRuleResponseDto mapToResponse(ProrationRuleMaster prorationRule) {

        return ProrationRuleResponseDto.builder()
                .prorationRuleId(prorationRule.getProrationRuleId())
                .prorationRuleCode(prorationRule.getProrationRuleCode())
                .prorationRuleName(prorationRule.getProrationRuleName())
                .description(prorationRule.getDescription())
                .isActive(prorationRule.getIsActive())
                .createdAt(prorationRule.getCreatedAt())
                .updatedAt(prorationRule.getUpdatedAt())
                .build();
    }
}
