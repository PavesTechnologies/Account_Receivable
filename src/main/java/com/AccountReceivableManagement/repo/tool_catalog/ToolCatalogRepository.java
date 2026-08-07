package com.AccountReceivableManagement.repo.tool_catalog;

import com.AccountReceivableManagement.entity.tool_catalog.ToolCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ToolCatalogRepository extends JpaRepository<ToolCatalog, UUID> {

    Optional<ToolCatalog> findByAssetId(UUID assetId);

    boolean existsByAssetId(UUID assetId);

    List<ToolCatalog> findByIsActiveTrue();
}
