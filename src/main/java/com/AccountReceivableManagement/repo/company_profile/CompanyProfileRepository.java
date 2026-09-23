package com.AccountReceivableManagement.repo.company_profile;

import com.AccountReceivableManagement.entity.company_profile.CompanyProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanyProfileRepository extends JpaRepository<CompanyProfile, UUID> {

    Optional<CompanyProfile> findFirstByIsActiveTrueOrderByCreatedAtAsc();
}
