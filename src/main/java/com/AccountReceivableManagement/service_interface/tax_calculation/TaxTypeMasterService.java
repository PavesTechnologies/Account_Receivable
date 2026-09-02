package com.AccountReceivableManagement.service_interface.tax_calculation;

import com.AccountReceivableManagement.dto.tax_calculation.TaxTypeRequestDto;
import com.AccountReceivableManagement.dto.tax_calculation.TaxTypeResponseDto;

import java.util.List;
import java.util.UUID;

public interface TaxTypeMasterService {

    TaxTypeResponseDto createTaxType(TaxTypeRequestDto request);

    TaxTypeResponseDto updateTaxType(
            UUID taxTypeId,
            TaxTypeRequestDto request
    );

    TaxTypeResponseDto getTaxTypeById(UUID taxTypeId);

    List<TaxTypeResponseDto> getAllTaxTypes();

    List<TaxTypeResponseDto> getActiveTaxTypes();

    void deactivateTaxType(UUID taxTypeId);
}
