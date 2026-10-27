package com.AccountReceivableManagement.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One recent event from invoice approvals, invoice deliveries or billing setups. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityEventDto {

    /** INVOICE_APPROVAL, INVOICE_DELIVERY or BILLING_SETUP */
    private String type;
    private String title;
    /** Invoice number or project name the event relates to. */
    private String reference;
    /** Recorded actor; approval history currently stores SYSTEM. Null when unknown. */
    private String actor;
    private LocalDateTime occurredAt;
}
