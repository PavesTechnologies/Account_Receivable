package com.AccountReceivableManagement.cdc.service;

import com.AccountReceivableManagement.cdc.payload.CdcEventPayload;

public interface ClientDataProcessor {
    void process(CdcEventPayload payload);
}
