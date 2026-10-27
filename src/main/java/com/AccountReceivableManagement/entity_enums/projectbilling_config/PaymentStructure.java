package com.AccountReceivableManagement.entity_enums.projectbilling_config;

public enum PaymentStructure {

    FULL_PAYMENT,
    INSTALLMENTS,

    /**
     * Every payment entry bills one PMS milestone: the entry's pmsMilestoneId references a milestone synchronised
     * into project_milestone_reference. Needs migration V16 (payment_structure ENUM -> VARCHAR(30)) on existing databases.
     */
    MILESTONES
}
