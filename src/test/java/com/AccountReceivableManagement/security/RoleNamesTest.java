package com.AccountReceivableManagement.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The spelling contract between UMS and {@code hasAnyRole}. UMS issues
 * {@code Finance_Executive}; the annotations ask for {@code FINANCE_EXECUTIVE}.
 * If these two ever stop meeting in the middle, every holder of the role is
 * denied with no error explaining why - so the meeting point is pinned here.
 */
class RoleNamesTest {

    @Nested
    @DisplayName("canonical")
    class Canonical {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> FINANCE_EXECUTIVE")
        @ValueSource(strings = {
                "Finance_Executive",   // the spelling UMS actually sends
                "FINANCE_EXECUTIVE",
                "finance_executive",
                "Finance Executive",
                "finance-executive",
                "  Finance_Executive  ",
                "finance--executive",
                "finance _ executive"
        })
        void foldsCaseAndSeparators(String umsSpelling) {
            assertThat(RoleNames.canonical(umsSpelling)).isEqualTo("FINANCE_EXECUTIVE");
        }

        @Test
        void distinctRolesStayDistinct() {
            assertThat(RoleNames.canonical("Finance_Manager"))
                    .isNotEqualTo(RoleNames.canonical("Finance_Executive"));
        }

        @Test
        void normalisesSpellingOnly_neverIdentity() {
            // A near-miss is still a different role: this folds how a name is
            // written, it does not guess what was meant.
            assertThat(RoleNames.canonical("Finance_Exec")).isEqualTo("FINANCE_EXEC");
        }

        @Test
        void blankAndNullBecomeEmpty() {
            assertThat(RoleNames.canonical(null)).isEmpty();
            assertThat(RoleNames.canonical("   ")).isEmpty();
        }
    }

    @Nested
    @DisplayName("roleAuthority")
    class RoleAuthority {

        @Test
        void addsThePrefixHasAnyRoleLooksFor() {
            assertThat(RoleNames.roleAuthority("Finance_Executive")).isEqualTo("ROLE_FINANCE_EXECUTIVE");
        }

        @Test
        void doesNotPrefixTwiceWhenUmsAlreadySendsOne() {
            // ROLE_ROLE_X would deny everyone, silently.
            assertThat(RoleNames.roleAuthority("ROLE_Finance_Manager")).isEqualTo("ROLE_FINANCE_MANAGER");
        }

        @Test
        void blankYieldsNoAuthority() {
            // Never an authority literally named "ROLE_".
            assertThat(RoleNames.roleAuthority("  ")).isEmpty();
            assertThat(RoleNames.roleAuthority(null)).isEmpty();
        }

        @Test
        void matchesTheConstantsTheAnnotationsUse() {
            assertThat(RoleNames.roleAuthority("Finance_Executive"))
                    .isEqualTo(RoleNames.ROLE_PREFIX + ArRoles.FINANCE_EXECUTIVE);
            assertThat(RoleNames.roleAuthority("Finance_Manager"))
                    .isEqualTo(RoleNames.ROLE_PREFIX + ArRoles.FINANCE_MANAGER);
        }
    }
}
