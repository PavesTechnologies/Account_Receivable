package com.AccountReceivableManagement.service_interface.tool_charge_acquisition;

import com.AccountReceivableManagement.dto.tool_charge_acquisition.ToolChargeAcquisitionRequestDto;
import com.AccountReceivableManagement.dto.tool_charge_acquisition.ToolChargePreviewDto;

import java.util.List;

public interface ToolChargeAcquisitionService {

    List<ToolChargePreviewDto> acquireCharges(ToolChargeAcquisitionRequestDto request);
}
