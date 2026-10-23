package com.AccountReceivableManagement.util;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingTMRateCard;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Role and date rules shared by the rate-card service and the approval gate, so both apply exactly the same
 * conventions as rate resolution ({@code TimeAndMaterialBillingStrategy}): roles match on trimmed,
 * case-insensitive names, and a null effective date is open-ended. Date ranges are inclusive on both ends.
 */
public final class TmRateCardRules {

    private TmRateCardRules() {
    }

    /** Trimmed role name; blank means "no role" (null), as rate resolution already treats it. */
    public static String normalizeRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return null;
        }
        return roleName.trim();
    }

    public static boolean sameRole(String a, String b) {
        String left = normalizeRole(a);
        String right = normalizeRole(b);
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    /** Inclusive ranges intersect; a null bound is open-ended. Ranges ending/starting on the same day overlap. */
    public static boolean overlaps(LocalDate aFrom, LocalDate aTo, LocalDate bFrom, LocalDate bTo) {
        boolean aStartsBeforeBEnds = aFrom == null || bTo == null || !aFrom.isAfter(bTo);
        boolean bStartsBeforeAEnds = bFrom == null || aTo == null || !bFrom.isAfter(aTo);
        return aStartsBeforeBEnds && bStartsBeforeAEnds;
    }

    /** True when [from, to] lies entirely inside [outerFrom, outerTo]; null outer bounds are open-ended. */
    public static boolean within(LocalDate from, LocalDate to, LocalDate outerFrom, LocalDate outerTo) {
        boolean startOk = outerFrom == null || !from.isBefore(outerFrom);
        boolean endOk = outerTo == null || !to.isAfter(outerTo);
        return startOk && endOk;
    }

    /**
     * Rejects a card when another active card of the same kind (other than {@code excludeCardId}) has an
     * overlapping window. "Same kind" is the same role (trimmed, case-insensitive) or, for a card without a role
     * (a Standard card), any other role-less card: billing resolves exactly one card per work date and fails when
     * two match, so such a schedule could never be billed. Different roles - and role-specific versus role-less
     * cards - may overlap freely.
     */
    public static void assertNoRoleConflict(List<BillingTMRateCard> activeCards, UUID excludeCardId, String role,
                                            LocalDate from, LocalDate to) {
        if (activeCards == null) {
            return;
        }
        if (normalizeRole(role) == null) {
            assertNoRoleLessConflict(activeCards, excludeCardId, from, to);
            return;
        }
        for (BillingTMRateCard other : activeCards) {
            if (excludeCardId != null && excludeCardId.equals(other.getRateCardId())) {
                continue;
            }
            if (sameRole(role, other.getRoleName())
                    && overlaps(from, to, other.getEffectiveFrom(), other.getEffectiveTo())) {
                throw new GlobalExceptionHandler.ValidationException(
                        "A rate card for role '" + normalizeRole(role) + "' already exists for an overlapping period ("
                                + describe(other.getEffectiveFrom(), other.getEffectiveTo())
                                + "). A role can have only one active rate card on any date; change the effective "
                                + "dates or edit the existing card.");
            }
        }
    }

    private static void assertNoRoleLessConflict(List<BillingTMRateCard> activeCards, UUID excludeCardId,
                                                 LocalDate from, LocalDate to) {
        for (BillingTMRateCard other : activeCards) {
            if (excludeCardId != null && excludeCardId.equals(other.getRateCardId())) {
                continue;
            }
            if (normalizeRole(other.getRoleName()) == null
                    && overlaps(from, to, other.getEffectiveFrom(), other.getEffectiveTo())) {
                throw new GlobalExceptionHandler.ValidationException(
                        "A Standard rate card (no role) already exists for an overlapping period ("
                                + describe(other.getEffectiveFrom(), other.getEffectiveTo())
                                + "). Only one Standard rate can apply on any date; change the effective dates "
                                + "(for example end the existing card the day before) or edit the existing card.");
            }
        }
    }

    private static String describe(LocalDate from, LocalDate to) {
        return (from != null ? from.toString() : "no start date") + " to " + (to != null ? to.toString() : "no end date");
    }
}
