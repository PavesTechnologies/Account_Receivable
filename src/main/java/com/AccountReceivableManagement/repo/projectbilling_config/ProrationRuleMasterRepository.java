package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.ProrationRuleMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProrationRuleMasterRepository extends JpaRepository<ProrationRuleMaster, UUID> {
    Optional<ProrationRuleMaster> findByProrationRuleCodeIgnoreCase(String prorationRuleCode);

    Optional<ProrationRuleMaster> findByProrationRuleNameIgnoreCase(String prorationRuleName);

    List<ProrationRuleMaster> findByIsActiveTrueOrderByProrationRuleCodeAsc();

    boolean existsByProrationRuleCodeIgnoreCase(String prorationRuleCode);

    boolean existsByProrationRuleNameIgnoreCase(String prorationRuleName);
}
