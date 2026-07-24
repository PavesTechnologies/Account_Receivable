package com.AccountReceivableManagement.service;

import com.AccountReceivableManagement.CDC.payload.CdcEventPayload;

public interface ClientDataProcessor {
    void process(CdcEventPayload payload);
}
