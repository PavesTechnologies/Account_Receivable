package com.AccountReceivableManagement.security;

import com.AccountReceivableManagement.dto.UserDTO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Backend enforcement of who may take financial decisions. Called by the
 * services that perform them - not only by controllers - so no entry point
 * can bypass it. The identity is always the authenticated UMS user (JWT),
 * never a value from the request; a record lock never grants a permission.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinancialActionAuthorizer {

    private static final int LABEL_MAX_LENGTH = 100;

    private final CurrentUserService currentUserService;
    private final FinancialSecurityProperties properties;

    @PostConstruct
    void registerWithActorContext() {
        ActorContext.registerCurrentUserIdResolver(currentUserService::getUserId);
        for (FinancialAction action : FinancialAction.values()) {
            if (configured(action.authorities(properties.getAuthorization())).isEmpty()) {
                log.warn("[FinancialAuth] no authorities configured for {}: nobody can {} until its "
                        + "ar.security.authorization.* property (AR_AUTH_*) is set.", action, action.description());
            }
        }
    }

    /**
     * The authenticated user, if they hold one of the authorities configured
     * for {@code action}.
     *
     * @throws AccessDeniedException (403) otherwise - including for the
     *         system, which never takes these decisions
     */
    public Actor requirePermission(FinancialAction action) {
        if (ActorContext.isSystem()) {
            throw new AccessDeniedException("Automatic billing cannot " + action.description() + ".");
        }
        UserDTO user = currentUserService.getCurrentUser();
        Set<String> required = configured(action.authorities(properties.getAuthorization()));
        Set<String> held = heldAuthorities(user);
        if (required.stream().noneMatch(held::contains)) {
            // Claim values only (never the token), so a role/permission name mismatch can be diagnosed.
            log.warn("[FinancialAuth] denied {} to user {}: holds {}, requires one of {}",
                    action, user.getId(), held, required.isEmpty() ? "(none configured)" : required);
            throw new AccessDeniedException("You are not authorized to " + action.description() + ".");
        }
        return actorOf(user);
    }

    /**
     * Maker-checker: refuses when the approver is the user who submitted the
     * record. A record submitted by the system (no user id) has no maker.
     */
    public void requireDifferentFromMaker(Long makerUserId, Actor approver, String record) {
        if (properties.getAuthorization().isMakerChecker() && makerUserId != null
                && Objects.equals(makerUserId, approver.userId())) {
            throw new AccessDeniedException("You submitted this " + record
                    + " for approval, so it must be approved by another authorized user.");
        }
    }

    /** Who is acting now: the authenticated user, or the system (automatic billing, no user). */
    public Actor currentActor() {
        if (ActorContext.isSystem()) {
            return Actor.SYSTEM;
        }
        try {
            return actorOf(currentUserService.getCurrentUser());
        } catch (RuntimeException noAuthenticatedUser) {
            return Actor.SYSTEM;
        }
    }

    /**
     * The user's JWT authorities in canonical form: each entry of the "roles"
     * claim as ROLE_&lt;role&gt; (a role UMS already sends as ROLE_x is not
     * prefixed twice), each entry of the "permissions" claim as is.
     */
    private static Set<String> heldAuthorities(UserDTO user) {
        Set<String> held = new TreeSet<>();
        if (user.getRoles() != null) {
            user.getRoles().stream().map(FinancialActionAuthorizer::canonical).filter(r -> !r.isEmpty())
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role).forEach(held::add);
        }
        if (user.getPermissions() != null) {
            user.getPermissions().stream().map(FinancialActionAuthorizer::canonical).filter(p -> !p.isEmpty())
                    .forEach(held::add);
        }
        return held;
    }

    private static Set<String> configured(List<String> allowed) {
        Set<String> required = new TreeSet<>();
        if (allowed != null) {
            allowed.stream().map(FinancialActionAuthorizer::canonical).filter(a -> !a.isEmpty()).forEach(required::add);
        }
        return required;
    }

    /**
     * UMS role/permission names are matched regardless of case and of space,
     * hyphen or underscore separators ("Finance Manager", "finance-manager" and
     * "FINANCE_MANAGER" are the same role), as {@link FinanceExecutiveAuthorizer}
     * already does for its role. The name itself must still match exactly.
     */
    private static String canonical(String authority) {
        return authority == null ? ""
                : authority.trim().replaceAll("[\\s\\-_]+", "_").toUpperCase(java.util.Locale.ROOT);
    }

    private static Actor actorOf(UserDTO user) {
        String label = user.getEmail() != null && !user.getEmail().isBlank() ? user.getEmail()
                : user.getName() != null && !user.getName().isBlank() ? user.getName()
                : "user " + user.getId();
        if (label.length() > LABEL_MAX_LENGTH) {
            label = label.substring(0, LABEL_MAX_LENGTH);
        }
        return new Actor(user.getId(), label);
    }
}
