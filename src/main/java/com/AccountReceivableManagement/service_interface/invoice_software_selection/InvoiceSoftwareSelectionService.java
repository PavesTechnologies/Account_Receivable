package com.AccountReceivableManagement.service_interface.invoice_software_selection;

import com.AccountReceivableManagement.dto.invoice_software_selection.InvoiceSoftwareSelectionResponseDto;

import java.util.List;

public interface InvoiceSoftwareSelectionService {

    List<InvoiceSoftwareSelectionResponseDto> getSelectableAssets(Long projectId);
}
