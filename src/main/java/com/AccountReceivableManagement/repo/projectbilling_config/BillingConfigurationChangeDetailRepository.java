package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfigurationChangeDetail;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfigurationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BillingConfigurationChangeDetailRepository extends JpaRepository<BillingConfigurationChangeDetail, UUID> {

    List<BillingConfigurationChangeDetail> findBySnapshotOrderByCreatedAtAsc(
            BillingConfigurationSnapshot snapshot);
}
