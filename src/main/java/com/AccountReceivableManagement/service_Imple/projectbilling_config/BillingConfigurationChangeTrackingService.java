package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationChangeDto;
import com.AccountReceivableManagement.entity.projectbilling_config.*;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.*;
import com.AccountReceivableManagement.repo.projectbilling_config.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingConfigurationChangeTrackingService {

    private final BillingConfigurationSnapshotRepository snapshotRepository;
    private final BillingConfigurationChangeDetailRepository changeDetailRepository;
    private final BillingMilestonePlanRepository milestonePlanRepository;
    private final BillingPaymentEntryRepository paymentEntryRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Transactional
    public BillingConfigurationSnapshot createSnapshot(BillingConfiguration configuration) {
        BillingConfigurationSnapshot snapshot = BillingConfigurationSnapshot.builder()
                .billingConfiguration(configuration)
                .previousApprovalStatus(configuration.getApprovalStatus())
                .previousBillingStatus(configuration.getBillingStatus())
                .previousClient(configuration.getClient())
                .previousProject(configuration.getProject())
                .previousBillingType(configuration.getBillingType())
                .previousCurrency(configuration.getCurrency())
                .previousPaymentTerm(configuration.getPaymentTerm())
                .previousBillingFrequency(configuration.getBillingFrequency())
                .previousTaxRegion(configuration.getTaxRegion())
                .previousContractValue(configuration.getContractValue())
                .previousExpenseBillingEligible(configuration.getExpenseBillingEligible())
                .previousEffectiveFrom(configuration.getEffectiveFrom())
                .previousEffectiveTo(configuration.getEffectiveTo())
                .previousHourlyRate(configuration.getHourlyRate())
                .previousInvoiceGenerationType(configuration.getInvoiceGenerationType())
                .previousPricingModel(configuration.getPricingModel())
                .previousBillingContext(configuration.getBillingContext())
                .previousProductName(configuration.getProductName())
                .previousProductDescription(configuration.getProductDescription())
                .build();

        return snapshotRepository.save(snapshot);
    }

    @Transactional
    public void recordChange(BillingConfigurationSnapshot snapshot, String fieldName, 
                            String fieldDisplayName, String fieldType, 
                            Object previousValue, Object newValue, String category) {
        
        // Semantic comparison - check if values are actually different
        if (isSemanticallyEqual(previousValue, newValue)) {
            return;
        }

        String prevStr = formatValue(previousValue, fieldType);
        String newStr = formatValue(newValue, fieldType);

        BillingConfigurationChangeDetail detail = BillingConfigurationChangeDetail.builder()
                .snapshot(snapshot)
                .fieldName(fieldName)
                .fieldDisplayName(fieldDisplayName)
                .fieldType(fieldType)
                .previousValue(prevStr)
                .newValue(newStr)
                .category(category)
                .build();

        changeDetailRepository.save(detail);
    }

    /**
     * Performs semantic comparison of values to determine if they are actually equal.
     * Handles BigDecimal, LocalDate, Boolean, Enum, and String types appropriately.
     */
    private boolean isSemanticallyEqual(Object previousValue, Object newValue) {
        // Both null are equal
        if (previousValue == null && newValue == null) {
            return true;
        }
        
        // One null, one not - not equal
        if (previousValue == null || newValue == null) {
            return false;
        }
        
        // BigDecimal comparison - compare numeric value, not scale
        if (previousValue instanceof BigDecimal && newValue instanceof BigDecimal) {
            return ((BigDecimal) previousValue).compareTo((BigDecimal) newValue) == 0;
        }
        
        // LocalDate comparison
        if (previousValue instanceof LocalDate && newValue instanceof LocalDate) {
            return previousValue.equals(newValue);
        }
        
        // Boolean comparison
        if (previousValue instanceof Boolean && newValue instanceof Boolean) {
            return previousValue.equals(newValue);
        }
        
        // Enum comparison
        if (previousValue instanceof Enum && newValue instanceof Enum) {
            return previousValue.equals(newValue);
        }
        
        // String comparison with null/empty/blank normalization
        if (previousValue instanceof String && newValue instanceof String) {
            String prev = normalizeString((String) previousValue);
            String newStr = normalizeString((String) newValue);
            return prev.equals(newStr);
        }
        
        // Default to object equality
        return previousValue.equals(newValue);
    }

    /**
     * Normalizes string values for comparison.
     * Treats null, empty, and blank strings as equivalent.
     */
    private String normalizeString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        return value.trim();
    }

    private String formatValue(Object value, String fieldType) {
        if (value == null) {
            return null;
        }

        if (value instanceof LocalDate) {
            return ((LocalDate) value).format(DATE_FORMATTER);
        }

        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toPlainString();
        }

        if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        }

        if (value instanceof Boolean) {
            return value.toString();
        }

        return value.toString();
    }

    @Transactional
    public void trackGeneralConfigurationChanges(BillingConfigurationSnapshot snapshot,
                                                  BillingConfiguration previous,
                                                  BillingConfiguration current) {
        
        recordChange(snapshot, "contractValue", "Contract Value", "DECIMAL",
                previous.getContractValue(), current.getContractValue(), "COMMERCIAL");
        
        recordChange(snapshot, "effectiveFrom", "Effective From", "DATE",
                previous.getEffectiveFrom(), current.getEffectiveFrom(), "DATES");
        
        recordChange(snapshot, "effectiveTo", "Effective To", "DATE",
                previous.getEffectiveTo(), current.getEffectiveTo(), "DATES");
        
        recordChange(snapshot, "hourlyRate", "Hourly Rate", "DECIMAL",
                previous.getHourlyRate(), current.getHourlyRate(), "PRICING");
        
        recordChange(snapshot, "pricingModel", "Pricing Model", "ENUM",
                previous.getPricingModel(), current.getPricingModel(), "PRICING");
        
        recordChange(snapshot, "invoiceGenerationType", "Invoice Generation Type", "ENUM",
                previous.getInvoiceGenerationType(), current.getInvoiceGenerationType(), "BILLING");
        
        recordChange(snapshot, "expenseBillingEligible", "Expense Billing Eligible", "BOOLEAN",
                previous.getExpenseBillingEligible(), current.getExpenseBillingEligible(), "BILLING");
        
        recordChange(snapshot, "billingFrequency", "Billing Frequency", "REFERENCE",
                previous.getBillingFrequency() != null ? previous.getBillingFrequency().getBillingFrequencyName() : null,
                current.getBillingFrequency() != null ? current.getBillingFrequency().getBillingFrequencyName() : null,
                "BILLING");
        
        recordChange(snapshot, "paymentTerm", "Payment Terms", "REFERENCE",
                previous.getPaymentTerm() != null ? previous.getPaymentTerm().getPaymentTermName() : null,
                current.getPaymentTerm() != null ? current.getPaymentTerm().getPaymentTermName() : null,
                "COMMERCIAL");
        
        recordChange(snapshot, "taxRegion", "Tax Region", "REFERENCE",
                previous.getTaxRegion() != null ? previous.getTaxRegion().getTaxRegionName() : null,
                current.getTaxRegion() != null ? current.getTaxRegion().getTaxRegionName() : null,
                "TAX");
    }

    @Transactional
    public void trackMilestonePlanChanges(BillingConfigurationSnapshot snapshot,
                                          List<BillingPaymentEntry> previousEntries,
                                          List<BillingPaymentEntry> newEntries) {
        
        for (int i = 0; i < Math.max(previousEntries.size(), newEntries.size()); i++) {
            if (i < previousEntries.size() && i < newEntries.size()) {
                BillingPaymentEntry prev = previousEntries.get(i);
                BillingPaymentEntry curr = newEntries.get(i);
                
                recordChange(snapshot, 
                        "payment_" + (i + 1) + "_percentage", 
                        "Payment " + (i + 1) + " - Percentage", 
                        "DECIMAL",
                        prev.getPercentage(), curr.getPercentage(), "MILESTONE");
                
                recordChange(snapshot, 
                        "payment_" + (i + 1) + "_amount", 
                        "Payment " + (i + 1) + " - Amount", 
                        "DECIMAL",
                        prev.getAmount(), curr.getAmount(), "MILESTONE");
                
                recordChange(snapshot, 
                        "payment_" + (i + 1) + "_billingDate", 
                        "Payment " + (i + 1) + " - Billing Date", 
                        "DATE",
                        prev.getBillingDate(), curr.getBillingDate(), "MILESTONE");
                
                recordChange(snapshot, 
                        "payment_" + (i + 1) + "_remarks", 
                        "Payment " + (i + 1) + " - Remarks", 
                        "STRING",
                        prev.getRemarks(), curr.getRemarks(), "MILESTONE");
            } else if (i < previousEntries.size()) {
                recordChange(snapshot, 
                        "payment_" + (i + 1), 
                        "Payment " + (i + 1), 
                        "STRING",
                        previousEntries.get(i).toString(), null, "MILESTONE");
            } else {
                recordChange(snapshot, 
                        "payment_" + (i + 1), 
                        "Payment " + (i + 1), 
                        "STRING",
                        null, newEntries.get(i).toString(), "MILESTONE");
            }
        }
    }

    public List<BillingConfigurationChangeDto> getChangesForConfiguration(BillingConfiguration configuration) {
        List<BillingConfigurationChangeDto> changes = new ArrayList<>();
        
        snapshotRepository.findFirstByBillingConfigurationOrderByCreatedAtDesc(configuration)
                .ifPresent(snapshot -> {
                    List<BillingConfigurationChangeDetail> details = 
                            changeDetailRepository.findBySnapshotOrderByCreatedAtAsc(snapshot);
                    
                    for (BillingConfigurationChangeDetail detail : details) {
                        changes.add(BillingConfigurationChangeDto.builder()
                                .field(detail.getFieldDisplayName())
                                .previousValue(detail.getPreviousValue())
                                .newValue(detail.getNewValue())
                                .category(detail.getCategory())
                                .build());
                    }
                });
        
        return changes;
    }

    @Transactional
    public void markSnapshotAsRestored(BillingConfigurationSnapshot snapshot) {
        snapshot.setIsRestored(true);
        snapshotRepository.save(snapshot);
    }

    @Transactional
    public void restoreConfigurationFromSnapshot(BillingConfiguration configuration, 
                                                 BillingConfigurationSnapshot snapshot) {
        configuration.setApprovalStatus(snapshot.getPreviousApprovalStatus());
        configuration.setBillingStatus(snapshot.getPreviousBillingStatus());
        configuration.setClient(snapshot.getPreviousClient());
        configuration.setProject(snapshot.getPreviousProject());
        configuration.setBillingType(snapshot.getPreviousBillingType());
        configuration.setCurrency(snapshot.getPreviousCurrency());
        configuration.setPaymentTerm(snapshot.getPreviousPaymentTerm());
        configuration.setBillingFrequency(snapshot.getPreviousBillingFrequency());
        configuration.setTaxRegion(snapshot.getPreviousTaxRegion());
        configuration.setContractValue(snapshot.getPreviousContractValue());
        configuration.setExpenseBillingEligible(snapshot.getPreviousExpenseBillingEligible());
        configuration.setEffectiveFrom(snapshot.getPreviousEffectiveFrom());
        configuration.setEffectiveTo(snapshot.getPreviousEffectiveTo());
        configuration.setHourlyRate(snapshot.getPreviousHourlyRate());
        configuration.setInvoiceGenerationType(snapshot.getPreviousInvoiceGenerationType());
        configuration.setPricingModel(snapshot.getPreviousPricingModel());
        configuration.setBillingContext(snapshot.getPreviousBillingContext());
        configuration.setProductName(snapshot.getPreviousProductName());
        configuration.setProductDescription(snapshot.getPreviousProductDescription());
        
        // Restore milestone plan entries if this is a Milestone Plan configuration
        if (configuration.getBillingType() != null && 
            "Milestone Plan".equalsIgnoreCase(configuration.getBillingType().getBillingTypeName())) {
            restoreMilestonePlanEntries(configuration);
        }
        
        markSnapshotAsRestored(snapshot);
    }

    @Transactional
    private void restoreMilestonePlanEntries(BillingConfiguration configuration) {
        BillingMilestonePlan milestonePlan = milestonePlanRepository
                .findByBillingConfigurationAndIsActiveTrue(configuration)
                .orElse(null);
        
        if (milestonePlan == null) {
            log.warn("No active milestone plan found for configuration {}", configuration.getBillingConfigurationId());
            return;
        }
        
        // Get all payment entries for this milestone plan (both active and inactive)
        List<BillingPaymentEntry> allEntries = paymentEntryRepository
                .findByMilestonePlan(milestonePlan);
        
        // Separate into currently active (new) and inactive (previous)
        List<BillingPaymentEntry> currentActiveEntries = allEntries.stream()
                .filter(BillingPaymentEntry::getIsActive)
                .toList();
        
        List<BillingPaymentEntry> previousInactiveEntries = allEntries.stream()
                .filter(e -> !e.getIsActive())
                .toList();
        
        // Deactivate current entries (the ones created during the edit)
        currentActiveEntries.forEach(entry -> entry.setIsActive(false));
        paymentEntryRepository.saveAll(currentActiveEntries);
        
        // Reactivate previous entries (the ones from before the edit)
        previousInactiveEntries.forEach(entry -> entry.setIsActive(true));
        paymentEntryRepository.saveAll(previousInactiveEntries);
        
        log.info("Restored milestone plan entries for configuration {}: deactivated {} current entries, reactivated {} previous entries",
                configuration.getBillingConfigurationId(), currentActiveEntries.size(), previousInactiveEntries.size());
    }
}
