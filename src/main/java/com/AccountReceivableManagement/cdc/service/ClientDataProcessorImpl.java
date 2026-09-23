package com.AccountReceivableManagement.cdc.service;

import com.AccountReceivableManagement.cdc.mapping.ClientCdcMappingRegistry;
import com.AccountReceivableManagement.cdc.mapping.ColumnMapping;
import com.AccountReceivableManagement.cdc.parsing.CdcValueConverter;
import com.AccountReceivableManagement.cdc.payload.CdcEventPayload;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.repo.client.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientDataProcessorImpl implements ClientDataProcessor {

    private final ClientRepository clientRepository;
    private final CdcValueConverter valueConverter;

    @Override
    @Transactional
    public void process(CdcEventPayload payload) {
        String operation = payload.getOperation();
        UUID clientId = valueConverter.convertValue(
                payload.getEntityId(),
                ClientCdcMappingRegistry.RMS_TO_AR.get("client_id")
        ) instanceof UUID uuid ? uuid : null;

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

        Client client = new Client();
        updateClientFromMap(payload.getAfter(), client);
        
        // DEBUG: Log client entity values before save
        log.debug("Client before save: clientId={}, countryCode={}, email={}, phoneNumber={}",
                client.getClientId(),
                client.getCountryCode(),
                client.getEmail(),
                client.getPhoneNumber());
        
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
        
        // DEBUG: Log client entity values before save
        log.debug("Client before update save: clientId={}, countryCode={}, email={}, phoneNumber={}",
                existingClient.getClientId(),
                existingClient.getCountryCode(),
                existingClient.getEmail(),
                existingClient.getPhoneNumber());
        
        clientRepository.save(existingClient);
        log.info("Updated client: {}", clientId);
    }

    private void updateClientFromMap(Map<String, Object> data, Client client) {

        for (Map.Entry<String, Object> entry : data.entrySet()) {

            // Find the mapping for the RMS column
            ColumnMapping mapping = ClientCdcMappingRegistry.RMS_TO_AR.get(entry.getKey());

            // Skip unmapped columns
            if (mapping == null) {
                log.debug("No mapping found for RMS column: {}", entry.getKey());
                continue;
            }

            try {
                // Convert the value based on the mapping
                Object convertedValue = valueConverter.convertValue(entry.getValue(), mapping);

                // DEBUG: Log the mapping for the new fields
                if (entry.getKey().equals("country_code") || entry.getKey().equals("email") || entry.getKey().equals("phone_number")) {
                    log.debug("Mapping {} -> {}: sourceValue={}, convertedValue={}",
                            entry.getKey(),
                            mapping.getTargetField(),
                            entry.getValue(),
                            convertedValue);
                }

                // Find the target field in Client entity
                Field field = Client.class.getDeclaredField(mapping.getTargetField());
                field.setAccessible(true);

                // Set the converted value
                field.set(client, convertedValue);

                // DEBUG: Verify the field was set correctly
                if (entry.getKey().equals("country_code") || entry.getKey().equals("email") || entry.getKey().equals("phone_number")) {
                    Object actualValue = field.get(client);
                    log.debug("Field {} set successfully. Actual value in entity: {}", mapping.getTargetField(), actualValue);
                }

            } catch (Exception e) {
                log.error("Failed to map column '{}' to field '{}'",
                        mapping.getSourceColumn(),
                        mapping.getTargetField(),
                        e);

                throw new RuntimeException(
                        "Failed to map field: " + mapping.getTargetField(), e);
            }
        }
    }

    private void handleDelete(UUID clientId) {
        clientRepository.findById(clientId).ifPresent(client -> {
            clientRepository.delete(client);
            log.info("Deleted client: {}", clientId);
        });
    }
}
