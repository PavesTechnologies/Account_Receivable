package com.AccountReceivableManagement.service;

import com.AccountReceivableManagement.CDC.payload.CdcEventPayload;

public interface ProjectDataProcessor {
    void process(CdcEventPayload payload);
}
