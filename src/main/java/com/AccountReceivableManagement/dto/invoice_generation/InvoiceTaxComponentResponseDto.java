package com.AccountReceivableManagement.dto.invoice_generation;

import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxApplicabilityType;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;

@Builder
public class InvoiceTaxComponentResponseDto {

    private UUID invoiceTaxComponentId;

    private UUID taxTypeId;

    private String taxTypeCode;

    private String taxTypeName;

    private BigDecimal appliedRate;

    private BigDecimal taxAmount;

    private TaxApplicabilityType applicabilityType;

    public InvoiceTaxComponentResponseDto() {
    }

    public InvoiceTaxComponentResponseDto(UUID invoiceTaxComponentId, UUID taxTypeId, String taxTypeCode, String taxTypeName, BigDecimal appliedRate, BigDecimal taxAmount, TaxApplicabilityType applicabilityType) {
        this.invoiceTaxComponentId = invoiceTaxComponentId;
        this.taxTypeId = taxTypeId;
        this.taxTypeCode = taxTypeCode;
        this.taxTypeName = taxTypeName;
        this.appliedRate = appliedRate;
        this.taxAmount = taxAmount;
        this.applicabilityType = applicabilityType;
    }

    public UUID getInvoiceTaxComponentId() {
        return invoiceTaxComponentId;
    }

    public void setInvoiceTaxComponentId(UUID invoiceTaxComponentId) {
        this.invoiceTaxComponentId = invoiceTaxComponentId;
    }

    public UUID getTaxTypeId() {
        return taxTypeId;
    }

    public void setTaxTypeId(UUID taxTypeId) {
        this.taxTypeId = taxTypeId;
    }

    public String getTaxTypeCode() {
        return taxTypeCode;
    }

    public void setTaxTypeCode(String taxTypeCode) {
        this.taxTypeCode = taxTypeCode;
    }

    public String getTaxTypeName() {
        return taxTypeName;
    }

    public void setTaxTypeName(String taxTypeName) {
        this.taxTypeName = taxTypeName;
    }

    public BigDecimal getAppliedRate() {
        return appliedRate;
    }

    public void setAppliedRate(BigDecimal appliedRate) {
        this.appliedRate = appliedRate;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public TaxApplicabilityType getApplicabilityType() {
        return applicabilityType;
    }

    public void setApplicabilityType(TaxApplicabilityType applicabilityType) {
        this.applicabilityType = applicabilityType;
    }
}
