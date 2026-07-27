package com.AccountReceivableManagement.Dependency.billing_data_acquisition;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Temporary stand-in for the shared CDC master-data lookup. Always returns
 * the same fixed client id. To be replaced once Project/Client entities and
 * repositories exist in the shared master-data module — no other class in
 * Epic 2 should need to change when that happens.
 */
@Service
public class ProjectMasterDataServiceImpl implements ProjectMasterDataService {

    private static final UUID MOCK_CLIENT_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    // TODO: Replace with real ProjectRepository/ClientRepository lookup once the shared CDC module exists.
    @Override
    public UUID getClientIdByProjectId(Long projectId) {
        return MOCK_CLIENT_ID;
    }
}
