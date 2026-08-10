package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.ProrationRuleRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.ProrationRuleResponseDto;

import java.util.List;
import java.util.UUID;

public interface ProrationRuleMasterService {

    ProrationRuleResponseDto createProrationRule(ProrationRuleRequestDto request);

    ProrationRuleResponseDto updateProrationRule(UUID prorationRuleId,
                                                  ProrationRuleRequestDto request);

    ProrationRuleResponseDto getProrationRuleById(UUID prorationRuleId);

    List<ProrationRuleResponseDto> getAllProrationRules();

    List<ProrationRuleResponseDto> getActiveProrationRules();

    void deleteProrationRule(UUID prorationRuleId);
}
