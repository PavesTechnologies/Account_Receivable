package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationChangeDto;
import com.AccountReceivableManagement.entity.projectbilling_config.*;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.*;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.project.ProjectMilestoneReferenceRepository;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingConfigurationChangeTrackingService {

    private final BillingConfigurationSnapshotRepository snapshotRepository;
    private final BillingConfigurationChangeDetailRepository changeDetailRepository;
    private final BillingMilestonePlanRepository milestonePlanRepository;
    private final BillingPaymentEntryRepository paymentEntryRepository;
    private final ProjectMilestoneReferenceRepository milestoneReferenceRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Bookkeeping rows stored in the change-detail table (no schema change): they record EXACTLY which payment entries
     * were active when the snapshot was taken, so a rejection can restore that set and nothing else. They are not
     * user-visible changes - {@link #getChangesForConfiguration} filters them out. One row per entry (field name
     * {@code prior_entry_<entryId>}, entry id in previousValue) plus a sentinel row, which says "the set was captured"
     * and so tells an empty set apart from a snapshot taken before this existed.
     */
    static final String PRIOR_ENTRY_CATEGORY = "PRIOR_ENTRY_SET";
    static final String PRIOR_ENTRY_SENTINEL = "prior_entry_set";
    static final String PRIOR_ENTRY_PREFIX = "prior_entry_";

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

        BillingConfigurationSnapshot saved = snapshotRepository.save(snapshot);
        capturePriorEntrySet(saved, configuration);
        return saved;
    }

    /** Called before the edit deactivates anything: the plan's active entries now are the set a rejection must restore. */
    private void capturePriorEntrySet(BillingConfigurationSnapshot snapshot, BillingConfiguration configuration) {
        if (!isMilestonePlan(configuration)) {
            return;
        }
        List<BillingPaymentEntry> active = milestonePlanRepository
                .findByBillingConfigurationAndIsActiveTrue(configuration)
                .map(paymentEntryRepository::findByMilestonePlanAndIsActiveTrue)
                .orElse(List.of());

        saveMarker(snapshot, PRIOR_ENTRY_SENTINEL, null);
        for (BillingPaymentEntry entry : active) {
            saveMarker(snapshot, PRIOR_ENTRY_PREFIX + entry.getPaymentEntryId(), entry.getPaymentEntryId().toString());
        }
    }

    private void saveMarker(BillingConfigurationSnapshot snapshot, String fieldName, String entryId) {
        changeDetailRepository.save(BillingConfigurationChangeDetail.builder()
                .snapshot(snapshot)
                .fieldName(fieldName)
                .fieldDisplayName("Prior payment entry set")
                .fieldType("ENTRY_ID")
                .previousValue(entryId)
                .category(PRIOR_ENTRY_CATEGORY)
                .build());
    }

    private static boolean isMilestonePlan(BillingConfiguration configuration) {
        return configuration.getBillingType() != null
                && "Milestone Plan".equalsIgnoreCase(configuration.getBillingType().getBillingTypeName());
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

                // A different PMS milestone with the same numbers must still show up for the approver
                // (both null for FULL_PAYMENT / INSTALLMENTS entries, which records nothing).
                recordChange(snapshot, 
                        "payment_" + (i + 1) + "_pmsMilestoneId", 
                        "Payment " + (i + 1) + " - PMS Milestone", 
                        "STRING",
                        prev.getPmsMilestoneId(), curr.getPmsMilestoneId(), "MILESTONE");
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
                        if (PRIOR_ENTRY_CATEGORY.equals(detail.getCategory())) {
                            continue; // restore bookkeeping, not a change the approver should review
                        }
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
            restoreMilestonePlanEntries(configuration, snapshot);
        }
        
        markSnapshotAsRestored(snapshot);
    }

    /**
     * Restores exactly the entries that were active when {@code snapshot} was taken - never "every inactive entry", which
     * would also revive entries of older generations - and refuses to do so if that would give back a milestone another
     * live plan has claimed in the meantime. It throws (rolling the whole rejection back, nothing changes) rather than
     * guess; a snapshot taken before the entry set was recorded is left alone with a warning.
     */
    private void restoreMilestonePlanEntries(BillingConfiguration configuration, BillingConfigurationSnapshot snapshot) {
        List<BillingConfigurationChangeDetail> markers = changeDetailRepository.findBySnapshotOrderByCreatedAtAsc(snapshot)
                .stream()
                .filter(d -> PRIOR_ENTRY_CATEGORY.equals(d.getCategory()))
                .toList();
        if (markers.stream().noneMatch(d -> PRIOR_ENTRY_SENTINEL.equals(d.getFieldName()))) {
            log.warn("Snapshot {} of configuration {} does not record which payment entries were active, so they cannot be "
                            + "restored exactly; the milestone plan entries are left as they are",
                    snapshot.getSnapshotId(), configuration.getBillingConfigurationId());
            return;
        }
        Set<UUID> priorIds = markers.stream()
                .filter(d -> d.getPreviousValue() != null)
                .map(d -> UUID.fromString(d.getPreviousValue()))
                .collect(Collectors.toSet());

        BillingMilestonePlan milestonePlan = milestonePlanRepository
                .findByBillingConfigurationAndIsActiveTrue(configuration)
                .orElse(null);
        if (milestonePlan == null) {
            log.warn("No active milestone plan found for configuration {}", configuration.getBillingConfigurationId());
            return;
        }

        List<BillingPaymentEntry> allEntries = paymentEntryRepository.findByMilestonePlan(milestonePlan);
        List<BillingPaymentEntry> toActivate = allEntries.stream()
                .filter(e -> priorIds.contains(e.getPaymentEntryId()))
                .toList();
        if (toActivate.size() != priorIds.size()) {
            throw new GlobalExceptionHandler.ValidationException(
                    "The previously approved payment entries can no longer be found, so the edit cannot be rejected "
                            + "without losing them. Nothing was changed.");
        }

        List<Long> milestoneIds = toActivate.stream()
                .map(BillingPaymentEntry::getPmsMilestoneId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (!milestoneIds.isEmpty()) {
            // same protocol as claiming: milestone rows first (ascending), then the locking conflict read
            milestoneReferenceRepository.findAllByIdForUpdate(milestoneIds);
            List<BillingPaymentEntry> conflicts = paymentEntryRepository
                    .findActiveAssignmentsInOtherConfigurations(milestoneIds, configuration.getBillingConfigurationId());
            if (!conflicts.isEmpty()) {
                throw new GlobalExceptionHandler.ValidationException(
                        "PMS milestone " + conflicts.get(0).getPmsMilestoneId() + " from the previously approved plan is now "
                                + "part of another billing plan, so this edit cannot be rejected without restoring a "
                                + "conflicting claim. Nothing was changed; release the milestone from the other plan first.");
            }
        }

        List<BillingPaymentEntry> toDeactivate = allEntries.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsActive()) && !priorIds.contains(e.getPaymentEntryId()))
                .toList();
        toDeactivate.forEach(e -> e.setIsActive(false));
        paymentEntryRepository.saveAll(toDeactivate);

        List<BillingPaymentEntry> reactivated = toActivate.stream()
                .filter(e -> !Boolean.TRUE.equals(e.getIsActive()))
                .toList();
        reactivated.forEach(e -> e.setIsActive(true));
        paymentEntryRepository.saveAll(reactivated);

        log.info("Restored milestone plan entries for configuration {}: deactivated {} entries of the pending edit, "
                        + "reactivated {} entries of the approved plan",
                configuration.getBillingConfigurationId(), toDeactivate.size(), reactivated.size());
    }
}
