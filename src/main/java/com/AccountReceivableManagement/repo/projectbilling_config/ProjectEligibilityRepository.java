package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectEligibilityRepository extends JpaRepository<BillingConfiguration, UUID> {

    /**
     * Finds project IDs that have billing configurations which make them
     * ineligible for creating a new billing configuration.
     *
     * A project is ineligible if it has any configuration with:
     * - APPROVED + ACTIVE (currently active)
     * - DRAFT (reserved for setup)
     * - PENDING_APPROVAL (reserved for approval)
     *
     * Projects with only EXPIRED or REJECTED configurations are eligible.
     */
    @Query("""
            SELECT DISTINCT bc.project.pmsProjectId
            FROM BillingConfiguration bc
            WHERE bc.client.clientId = :clientId
            AND (
                (bc.approvalStatus = 'APPROVED' AND bc.billingStatus = 'ACTIVE')
                OR (bc.approvalStatus = 'DRAFT')
                OR (bc.approvalStatus = 'PENDING_APPROVAL')
            )
            """)
    List<Long> findIneligibleProjectIdsByClientId(@Param("clientId") UUID clientId);
}
