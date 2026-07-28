package com.AccountReceivableManagement.repo.client;

import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity_enums.client.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    List<Client> findByStatusOrderByClientNameAsc(RecordStatus status);}
