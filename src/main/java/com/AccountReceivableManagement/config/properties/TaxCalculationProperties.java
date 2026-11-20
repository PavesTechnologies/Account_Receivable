package com.AccountReceivableManagement.config.properties;

import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxApplicabilityType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code ar.tax.*}: temporary overrides of tax applicability resolution.
 *
 * <p><strong>This class exists to be deleted.</strong> It carries the system
 * through the period in which project locations are known only at country
 * granularity, and should be removed once they carry state.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ar.tax")
public class TaxCalculationProperties {

    /**
     * Forces every transaction's applicability to this value instead of
     * comparing the source and destination tax jurisdictions. Unset (the
     * default) leaves the jurisdiction comparison in place.
     *
     * <p>Why it exists: a destination jurisdiction is resolved by matching a
     * project's primary location against a tax region <em>name</em>, so it can
     * only ever be a country. An Indian seller billing an Indian project
     * therefore always compares equal and is taxed {@code SAME_JURISDICTION} -
     * CGST + SGST - which is a claim about <em>state</em> that nothing in this
     * system actually knows. Setting this to {@code DIFFERENT_JURISDICTION}
     * taxes those transactions as IGST instead: the same 18% total, without
     * asserting a state split that was never determined.
     *
     * <p>{@code ALL} is the other useful value: it admits only components
     * configured as {@code ALL}, for a tax configuration built that way. Note
     * that it applies no component at all to a configuration whose components
     * are purely jurisdiction-specific, which fails the calculation.
     *
     * <p>Delete this property - and the branch that reads it - when project
     * locations carry state granularity. Nothing else depends on it.
     */
    private TaxApplicabilityType applicabilityOverride;
}
