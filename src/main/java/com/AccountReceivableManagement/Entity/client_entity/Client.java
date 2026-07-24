package com.AccountReceivableManagement.Entity.client_entity;

import com.AccountReceivableManagement.Entity_Enums.client.ClientType;
import com.AccountReceivableManagement.Entity_Enums.client.DeliveryModel;
import com.AccountReceivableManagement.Entity_Enums.client.PriorityLevel;
import com.AccountReceivableManagement.Entity_Enums.client.RecordStatus;
import com.AccountReceivableManagement.config.BooleanFromIntegerDeserializer;
import com.AccountReceivableManagement.config.CdcAwareEnumDeserializer;
import com.AccountReceivableManagement.config.UuidFromStringDeserializer;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "client")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class Client {
    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "client_id")
    @JsonProperty("client_id")
    @JsonDeserialize(using = UuidFromStringDeserializer.class)
    private UUID clientId;

    @Column(name = "client_name")
    private String clientName;

    @Enumerated(EnumType.STRING)
    @JsonDeserialize(using = CdcAwareEnumDeserializer.class)
    private ClientType clientType;

    @Enumerated(EnumType.STRING)
    @JsonDeserialize(using = CdcAwareEnumDeserializer.class)
    private PriorityLevel priorityLevel;

    @Enumerated(EnumType.STRING)
    @JsonDeserialize(using = CdcAwareEnumDeserializer.class)
    private DeliveryModel deliveryModel;

    @Column(name = "country_name")
    private String countryName;

    @Column(name = "default_timezone")
    private String defaultTimezone;

    @Enumerated(EnumType.STRING)
    @JsonDeserialize(using = CdcAwareEnumDeserializer.class)
    private RecordStatus status;

    @JsonDeserialize(using = BooleanFromIntegerDeserializer.class)
    private Boolean sla;

    @JsonDeserialize(using = BooleanFromIntegerDeserializer.class)
    private Boolean compliance;

    @JsonDeserialize(using = BooleanFromIntegerDeserializer.class)
    private Boolean escalationContact;

    @JsonDeserialize(using = BooleanFromIntegerDeserializer.class)
    private Boolean assets;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
