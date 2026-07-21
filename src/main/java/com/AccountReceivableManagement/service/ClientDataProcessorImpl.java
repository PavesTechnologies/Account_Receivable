package com.AccountReceivableManagement.service;

import com.AccountReceivableManagement.CDC.payload.CdcEventPayload;
import com.AccountReceivableManagement.Entity.client_entity.Client;
import com.AccountReceivableManagement.Repo.client.ClientRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientDataProcessorImpl implements ClientDataProcessor {

    private final ClientRepository clientRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void process(CdcEventPayload payload) {
        String operation = payload.getOperation();
        UUID clientId = UUID.fromString(payload.getEntityId());

        switch (operation) {
            case "c":
                handleCreate(payload, clientId);
                break;
            case "u":
                handleUpdate(payload, clientId);
                break;
            case "d":
                handleDelete(clientId);
                break;
            default:
                log.warn("Unknown operation: {}", operation);
                throw new IllegalArgumentException("Unknown operation: " + operation);
        }
    }

    private void handleCreate(CdcEventPayload payload, UUID clientId) {
        if (clientRepository.existsById(clientId)) {
            log.warn("Client with ID {} already exists. Skipping create operation.", clientId);
            return;
        }

        if (payload.getAfter() == null) {
            log.warn("Create event has no after data: {}", payload.getEntityId());
            return;
        }

        Client client = objectMapper.convertValue(payload.getAfter(), Client.class);
        clientRepository.save(client);
        log.info("Created client: {}", client.getClientId());
    }

    private void handleUpdate(CdcEventPayload payload, UUID clientId) {
        if (payload.getAfter() == null) {
            log.warn("Update event has no after data: {}", payload.getEntityId());
            return;
        }

        Client existingClient = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found for update: " + clientId));

        updateClientFromMap(payload.getAfter(), existingClient);
        clientRepository.save(existingClient);
        log.info("Updated client: {}", clientId);
    }

    private void updateClientFromMap(Map<String, Object> data, Client client) {
        objectMapper.convertValue(data, client);
    }

    private void handleDelete(UUID clientId) {
        clientRepository.findById(clientId).ifPresent(client -> {
            clientRepository.delete(client);
            log.info("Deleted client: {}", clientId);
        });
    }
}
