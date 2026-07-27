package com.AccountReceivableManagement.Dependency.billing_data_acquisition;

import java.util.UUID;

/**
 * Isolates Epic 2 from how the client for a project is actually resolved
 * from local CDC-synced master data (Client/Project), which Epic 2 does
 * not own. The Service depends only on this contract.
 */
public interface ProjectMasterDataService {

    UUID getClientIdByProjectId(Long projectId);
}
