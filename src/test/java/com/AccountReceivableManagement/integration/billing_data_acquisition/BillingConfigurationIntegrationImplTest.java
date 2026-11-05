package com.AccountReceivableManagement.integration.billing_data_acquisition;

import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class BillingConfigurationIntegrationImplTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Time & Material",
            "Time and Material",
            "Timesheet Based",
            "Timesheet",
            "TIME_AND_MATERIAL",
            "TIME_MATERIAL",
            "T_M"
    })
    void toBillingTypeEnum_timeAndMaterial_resolvesCorrectly(String input) {
        assertThat(BillingConfigurationIntegrationImpl.toBillingTypeEnum(input))
                .isEqualTo(BillingType.TIME_AND_MATERIAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Fixed Price",
            "Fixed",
            "FIXED_PRICE"
    })
    void toBillingTypeEnum_fixedPrice_resolvesCorrectly(String input) {
        assertThat(BillingConfigurationIntegrationImpl.toBillingTypeEnum(input))
                .isEqualTo(BillingType.FIXED_PRICE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Milestone",
            "Milestone Plan",
            "MILESTONE"
    })
    void toBillingTypeEnum_milestone_resolvesCorrectly(String input) {
        assertThat(BillingConfigurationIntegrationImpl.toBillingTypeEnum(input))
                .isEqualTo(BillingType.MILESTONE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Retainer",
            "Recurring",
            "Subscription",
            "RETAINER"
    })
    void toBillingTypeEnum_retainer_resolvesCorrectly(String input) {
        assertThat(BillingConfigurationIntegrationImpl.toBillingTypeEnum(input))
                .isEqualTo(BillingType.RETAINER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Manual",
            "MANUAL"
    })
    void toBillingTypeEnum_manual_resolvesCorrectly(String input) {
        assertThat(BillingConfigurationIntegrationImpl.toBillingTypeEnum(input))
                .isEqualTo(BillingType.MANUAL);
    }

    @Test
    void toBillingTypeEnum_nullOrBlank_returnsNull() {
        assertThat(BillingConfigurationIntegrationImpl.toBillingTypeEnum(null)).isNull();
        assertThat(BillingConfigurationIntegrationImpl.toBillingTypeEnum("")).isNull();
        assertThat(BillingConfigurationIntegrationImpl.toBillingTypeEnum("   ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Unknown",
            "Custom Strategy",
            "Unrecognized Type",
            "INVALID"
    })
    void toBillingTypeEnum_unknownTypes_returnNull_neverDefaultToTimeAndMaterial(String input) {
        // G-15: Unknown billing types must fail explicitly rather than silently defaulting to T&M
        assertThat(BillingConfigurationIntegrationImpl.toBillingTypeEnum(input)).isNull();
    }
}
