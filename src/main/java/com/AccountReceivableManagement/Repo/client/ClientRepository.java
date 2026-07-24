package com.AccountReceivableManagement.Repo.client;

import com.AccountReceivableManagement.Entity.client_entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {
}
