package com.AccountReceivableManagement.security;

import com.AccountReceivableManagement.dto.UserDTO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Backend enforcement of who may take financial decisions. Called by the
 * services that perform them - not only by controllers - so no entry point
 * can bypass it. The identity is always the authenticated UMS user (JWT),
 * never a value from the request; a record lock never grants a permission.
 */
@Component
@RequiredArgsConstructor
public class FinancialActionAuthorizer {

    private static final int LABEL_MAX_LENGTH = 100;

    private final CurrentUserService currentUserService;
    private final FinancialSecurityProperties properties;

    @PostConstruct
    void registerWithActorContext() {
        ActorContext.registerCurrentUserIdResolver(currentUserService::getUserId);
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
        if (!holdsAny(user, action.authorities(properties.getAuthorization()))) {
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

    private static boolean holdsAny(UserDTO user, List<String> allowed) {
        Set<String> held = new HashSet<>();
        if (user.getRoles() != null) {
            user.getRoles().stream().filter(Objects::nonNull).map(role -> "ROLE_" + role.trim()).forEach(held::add);
        }
        if (user.getPermissions() != null) {
            user.getPermissions().stream().filter(Objects::nonNull).map(String::trim).forEach(held::add);
        }
        return allowed.stream().filter(Objects::nonNull).map(String::trim)
                .anyMatch(authority -> !authority.isEmpty() && held.contains(authority));
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
