package com.AccountReceivableManagement.cdc.service;

import com.AccountReceivableManagement.cdc.parsing.CdcValueConverter;
import com.AccountReceivableManagement.cdc.payload.CdcEventPayload;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity_enums.client.RecordStatus;
import com.AccountReceivableManagement.repo.client.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Uses the real {@link CdcValueConverter} and an in-memory repository so the real client-id conversion
 * and field mappings are exercised.
 */
class ClientDataProcessorImplTest {

    private final Map<UUID, Client> store = new HashMap<>();
    private ClientRepository clientRepository;
    private ClientDataProcessorImpl processor;
    private UUID clientId;

    @BeforeEach
    void setUp() {
        clientRepository = mock(ClientRepository.class);
        when(clientRepository.findById(any())).thenAnswer(i -> Optional.ofNullable(store.get(i.<UUID>getArgument(0))));
        when(clientRepository.existsById(any())).thenAnswer(i -> store.containsKey(i.<UUID>getArgument(0)));
        when(clientRepository.save(any(Client.class))).thenAnswer(i -> {
            Client c = i.getArgument(0);
            store.put(c.getClientId(), c);
            return c;
        });
        doAnswer(i -> {
            store.remove(i.<Client>getArgument(0).getClientId());
            return null;
        }).when(clientRepository).delete(any(Client.class));

        processor = new ClientDataProcessorImpl(clientRepository, new CdcValueConverter());
        clientId = UUID.randomUUID();
    }

    private Map<String, Object> row(String name, String email) {
        Map<String, Object> row = new HashMap<>();
        row.put("client_id", clientId.toString());
        row.put("client_name", name);
        row.put("country_code", "+1");
        row.put("email", email);
        row.put("phone_number", "5551234");
        row.put("status", "ACTIVE");
        return row;
    }

    private CdcEventPayload event(String op, String entityId, Map<String, Object> before, Map<String, Object> after) {
        return CdcEventPayload.builder()
                .connectorName("RMS").entityType("RMS-CLIENT").operation(op)
                .entityId(entityId).before(before).after(after).build();
    }

    @Test
    void createInsertsClientWithMappedFields() {
        processor.process(event("c", clientId.toString(), null, row("Acme", "a@acme.test")));

        Client saved = store.get(clientId);
        assertNotNull(saved);
        assertEquals("Acme", saved.getClientName());
        assertEquals("+1", saved.getCountryCode());
        assertEquals("a@acme.test", saved.getEmail());
        assertEquals("5551234", saved.getPhoneNumber());
        assertEquals(RecordStatus.ACTIVE, saved.getStatus());
    }

    @Test
    void updateChangesExistingClient() {
        processor.process(event("c", clientId.toString(), null, row("Acme", "a@acme.test")));

        processor.process(event("u", clientId.toString(), null, row("Acme Corp", "b@acme.test")));

        assertEquals("Acme Corp", store.get(clientId).getClientName());
        assertEquals("b@acme.test", store.get(clientId).getEmail());
    }

    @Test
    void updateForMissingClientStillFails() {
        assertThrows(IllegalArgumentException.class,
                () -> processor.process(event("u", clientId.toString(), null, row("Acme", "a@acme.test"))));
    }

    @Test
    void deleteRemovesClient() {
        processor.process(event("c", clientId.toString(), null, row("Acme", "a@acme.test")));

        processor.process(event("d", clientId.toString(), row("Acme", "a@acme.test"), null));

        assertFalse(store.containsKey(clientId));
    }

    @Test
    void snapshotCreatesMissingClient() {
        processor.process(event("r", clientId.toString(), null, row("Acme", "a@acme.test")));

        Client saved = store.get(clientId);
        assertNotNull(saved);
        assertEquals(clientId, saved.getClientId());
        assertEquals("Acme", saved.getClientName());
        assertEquals("a@acme.test", saved.getEmail());
        assertEquals("+1", saved.getCountryCode());
        assertEquals("5551234", saved.getPhoneNumber());
    }

    @Test
    void snapshotUpdatesExistingClientWithoutCreatingDuplicate() {
        processor.process(event("c", clientId.toString(), null, row("Acme", "a@acme.test")));

        processor.process(event("r", clientId.toString(), null, row("Acme Renamed", "new@acme.test")));

        assertEquals(1, store.size());
        assertEquals("Acme Renamed", store.get(clientId).getClientName());
        assertEquals("new@acme.test", store.get(clientId).getEmail());
    }

    @Test
    void repeatedSnapshotIsIdempotent() {
        CdcEventPayload snapshot = event("r", clientId.toString(), null, row("Acme", "a@acme.test"));

        processor.process(snapshot);
        processor.process(snapshot);
        processor.process(snapshot);

        assertEquals(1, store.size());
        assertEquals("Acme", store.get(clientId).getClientName());
        assertEquals("a@acme.test", store.get(clientId).getEmail());
    }

    @Test
    void snapshotAcceptsDebeziumBase64ClientId() {
        // Debezium emits BINARY(16) as base64; inbox entity ids look like this in production.
        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putLong(clientId.getMostSignificantBits()).putLong(clientId.getLeastSignificantBits());
        String base64 = Base64.getEncoder().encodeToString(bb.array());
        Map<String, Object> after = row("Acme", "a@acme.test");
        after.put("client_id", base64);

        processor.process(event("r", base64, null, after));

        assertTrue(store.containsKey(clientId));
        assertEquals(clientId, store.get(clientId).getClientId());
    }

    @Test
    void snapshotWithoutAfterDataSavesNothing() {
        processor.process(event("r", clientId.toString(), null, null));

        verify(clientRepository, never()).save(any());
    }

    @Test
    void snapshotWithoutClientIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> processor.process(event("r", null, null, row("Acme", "a@acme.test"))));
        verify(clientRepository, never()).save(any());
    }

    @Test
    void unknownOperationIsStillRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> processor.process(event("x", clientId.toString(), null, row("Acme", "a@acme.test"))));
    }
}
