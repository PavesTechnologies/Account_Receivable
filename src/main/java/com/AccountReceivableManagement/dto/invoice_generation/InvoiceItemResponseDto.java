package com.AccountReceivableManagement.dto.invoice_generation;

import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import lombok.*;

@Getter
@Setter
@Builder
public class InvoiceItemResponseDto {

    private UUID invoiceItemId;

    /** 1-based position of the line within the invoice. */
    private Integer lineNumber;

    private BillingItemType itemType;

    private String itemName;

    private String sourceReferenceId;

    private String resourceName;

    private BigDecimal quantity;

    private BigDecimal rate;

    private BigDecimal amount;

    private LocalDate workDate;

    private String role;

    /** T&M lines only: the configured base rate, the discount applied to it, and the period it came from. */
    private BigDecimal baseRate;

    private String discountType;

    private BigDecimal discountValue;

    private BigDecimal discountAmount;

    private String discountPeriodName;

    public InvoiceItemResponseDto() {
    }

    public InvoiceItemResponseDto(UUID invoiceItemId, Integer lineNumber, BillingItemType itemType, String itemName, String sourceReferenceId, String resourceName, BigDecimal quantity, BigDecimal rate, BigDecimal amount, LocalDate workDate, String role, BigDecimal baseRate, String discountType, BigDecimal discountValue, BigDecimal discountAmount, String discountPeriodName) {
        this.invoiceItemId = invoiceItemId;
        this.lineNumber = lineNumber;
        this.itemType = itemType;
        this.itemName = itemName;
        this.sourceReferenceId = sourceReferenceId;
        this.resourceName = resourceName;
        this.quantity = quantity;
        this.rate = rate;
        this.amount = amount;
        this.workDate = workDate;
        this.role = role;
        this.baseRate = baseRate;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.discountAmount = discountAmount;
        this.discountPeriodName = discountPeriodName;
    }

    public UUID getInvoiceItemId() {
        return invoiceItemId;
    }

    public void setInvoiceItemId(UUID invoiceItemId) {
        this.invoiceItemId = invoiceItemId;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public BillingItemType getItemType() {
        return itemType;
    }

    public void setItemType(BillingItemType itemType) {
        this.itemType = itemType;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getSourceReferenceId() {
        return sourceReferenceId;
    }

    public void setSourceReferenceId(String sourceReferenceId) {
        this.sourceReferenceId = sourceReferenceId;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public BigDecimal getBaseRate() {
        return baseRate;
    }

    public void setBaseRate(BigDecimal baseRate) {
        this.baseRate = baseRate;
    }

    public String getDiscountType() {
        return discountType;
    }

    public void setDiscountType(String discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public String getDiscountPeriodName() {
        return discountPeriodName;
    }

    public void setDiscountPeriodName(String discountPeriodName) {
        this.discountPeriodName = discountPeriodName;
    }
}
