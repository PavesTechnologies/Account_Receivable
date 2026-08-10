package com.AccountReceivableManagement.service_interface.software_charge_generation;

import com.AccountReceivableManagement.dto.invoice_software_selection.InvoiceSoftwareSelectionResponseDto;
import com.AccountReceivableManagement.dto.software_charge_generation.SoftwareChargeLineDto;

import java.util.List;

public interface SoftwareChargeGenerationService {

    List<SoftwareChargeLineDto> generateChargeLines(List<InvoiceSoftwareSelectionResponseDto> selectedAssets);
}
