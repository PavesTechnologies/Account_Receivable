package com.AccountReceivableManagement.repo.client;

import com.AccountReceivableManagement.entity.client_entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {
}
