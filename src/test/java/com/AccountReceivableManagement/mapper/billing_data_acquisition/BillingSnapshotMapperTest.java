package com.AccountReceivableManagement.mapper.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotResponseDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshotItem;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit coverage of {@link BillingSnapshotMapper#toResponse}, the step
 * that turns a persisted {@link BillingSnapshot}'s {@code items} into the
 * {@code timesheets} array of the API response.
 */
class BillingSnapshotMapperTest {

    private final BillingSnapshotMapper mapper = new BillingSnapshotMapper();

    @Test
    void toResponse_snapshotWithItems_mapsEachItemToATimesheetLineItem() {
        BillingSnapshotItem item1 = BillingSnapshotItem.builder()
                .billingSnapshotItemId(UUID.randomUUID())
                .itemType(BillingItemType.TIME_ENTRY)
                .itemName("Jane Doe")
                .sourceReferenceId("TMS-001")
                .quantity(BigDecimal.valueOf(8))
                .rate(BigDecimal.valueOf(165))
                .amount(BigDecimal.valueOf(1320))
                .workDate(LocalDate.of(2026, 8, 10))
                .approvalStatus("APPROVED")
                .role("Developer")
                .build();

        BillingSnapshotItem item2 = BillingSnapshotItem.builder()
                .billingSnapshotItemId(UUID.randomUUID())
                .itemType(BillingItemType.TIME_ENTRY)
                .itemName("John Smith")
                .sourceReferenceId("TMS-002")
                .quantity(BigDecimal.valueOf(4))
                .rate(BigDecimal.valueOf(150))
                .amount(BigDecimal.valueOf(600))
                .workDate(LocalDate.of(2026, 8, 11))
                .approvalStatus("APPROVED")
                .role("Tester")
                .build();

        BillingSnapshot snapshot = BillingSnapshot.builder()
                .id(UUID.randomUUID())
                .snapshotNumber("BS-20260810120000")
                .status(BillingSnapshotStatus.READY_FOR_TAX)
                .totalAmount(BigDecimal.valueOf(1920))
                .items(List.of(item1, item2))
                .build();

        BillingConfigurationResponseDto configuration = BillingConfigurationResponseDto.builder()
                .billingTypeName("Timesheet Based")
                .currencyCode("USD")
                .build();

        BillingSnapshotResponseDto response = mapper.toResponse(snapshot, configuration);

        assertThat(response.getTimesheets()).hasSize(2);

        BillingSnapshotResponseDto.TimesheetLineItemDto mapped1 = response.getTimesheets().get(0);
        assertThat(mapped1.getEmployee()).isEqualTo("Jane Doe");
        assertThat(mapped1.getSourceReferenceId()).isEqualTo("TMS-001");
        assertThat(mapped1.getWorkDate()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(mapped1.getHours()).isEqualByComparingTo(BigDecimal.valueOf(8));
        assertThat(mapped1.getRate()).isEqualByComparingTo(BigDecimal.valueOf(165));
        assertThat(mapped1.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1320));
        assertThat(mapped1.getApprovalStatus()).isEqualTo("APPROVED");
        assertThat(mapped1.getRole()).isEqualTo("Developer");

        BillingSnapshotResponseDto.TimesheetLineItemDto mapped2 = response.getTimesheets().get(1);
        assertThat(mapped2.getEmployee()).isEqualTo("John Smith");
        assertThat(mapped2.getSourceReferenceId()).isEqualTo("TMS-002");
    }

    @Test
    void toResponse_snapshotWithNoItems_returnsEmptyTimesheetsList() {
        BillingSnapshot snapshot = BillingSnapshot.builder()
                .id(UUID.randomUUID())
                .snapshotNumber("BS-20260810120000")
                .status(BillingSnapshotStatus.READY_FOR_TAX)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BillingConfigurationResponseDto configuration = BillingConfigurationResponseDto.builder().build();

        BillingSnapshotResponseDto response = mapper.toResponse(snapshot, configuration);

        assertThat(response.getTimesheets()).isNotNull();
        assertThat(response.getTimesheets()).isEmpty();
    }
}
