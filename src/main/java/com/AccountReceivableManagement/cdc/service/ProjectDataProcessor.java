package com.AccountReceivableManagement.cdc.service;

import com.AccountReceivableManagement.cdc.payload.CdcEventPayload;

public interface ProjectDataProcessor {
    void process(CdcEventPayload payload);
}
