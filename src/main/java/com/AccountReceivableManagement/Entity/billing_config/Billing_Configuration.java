//package com.AccountReceivableManagement.Entity.billing_config;
//
//import jakarta.persistence.*;
//
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.util.UUID;
//
//@Entity
//@Table(name = "billing_configuration")
//public class Billing_Configuration {
//    @Id
//    @GeneratedValue
//    @Column(name = "billing_configuration_id")
//    private UUID billingConfigurationId;
//
//    @Column(name = "client_id", nullable = false)
//    private UUID clientId;
//
//    @Column(name = "project_id", nullable = false)
//    private UUID projectId;
//
//    @Enumerated(EnumType.STRING)
//    @Column(nullable = false)
//    private BillingConfigurationStatus status;
//
//    @Column(name = "effective_from", nullable = false)
//    private LocalDate effectiveFrom;
//
//    @Column(name = "effective_to")
//    private LocalDate effectiveTo;
//
//    @Column(name = "version_no")
//    private Integer versionNo;
//
//    @Column(name = "active_flag")
//    private Boolean activeFlag;
//
//    @Column(name = "created_by")
//    private String createdBy;
//
//    @Column(name = "created_date")
//    private LocalDateTime createdDate;
//
//    @Column(name = "updated_by")
//    private String updatedBy;
//
//    @Column(name = "updated_date")
//    private LocalDateTime updatedDate;
//}
