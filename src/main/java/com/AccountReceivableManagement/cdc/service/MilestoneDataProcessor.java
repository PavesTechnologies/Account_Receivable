package com.AccountReceivableManagement.cdc.service;

import com.AccountReceivableManagement.cdc.payload.CdcEventPayload;

public interface MilestoneDataProcessor {
    void process(CdcEventPayload payload);
}
