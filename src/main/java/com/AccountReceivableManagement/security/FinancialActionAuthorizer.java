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
            log.info("[FinancialAuth] {} requires one of {}", action, requiredAuthorities(action));
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
        Set<String> required = requiredAuthorities(action);
        Set<String> held = heldAuthorities(user);
        if (required.stream().noneMatch(held::contains)) {
            // Claim values only (never the token), so a role/permission name mismatch can be diagnosed.
            log.warn("[FinancialAuth] denied {} to user {}: holds {}, requires one of {}",
                    action, user.getId(), held, required);
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
            user.getRoles().stream().map(RoleNames::roleAuthority).filter(r -> !r.isEmpty()).forEach(held::add);
        }
        if (user.getPermissions() != null) {
            user.getPermissions().stream().map(RoleNames::canonical).filter(p -> !p.isEmpty()).forEach(held::add);
        }
        return held;
    }

    /**
     * Who may take {@code action}: the configured override when one is set,
     * otherwise the roles the action is bound to in {@link FinancialAction}.
     * Never empty, so a missing property can no longer lock everybody out.
     */
    private Set<String> requiredAuthorities(FinancialAction action) {
        Set<String> override = configured(action.authorities(properties.getAuthorization()));
        return override.isEmpty() ? configured(action.defaultAuthorities()) : override;
    }

    private static Set<String> configured(List<String> allowed) {
        Set<String> required = new TreeSet<>();
        if (allowed != null) {
            allowed.stream().map(RoleNames::canonical).filter(a -> !a.isEmpty()).forEach(required::add);
        }
        return required;
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
