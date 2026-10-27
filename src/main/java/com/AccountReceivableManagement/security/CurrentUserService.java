package com.AccountReceivableManagement.security;

import com.AccountReceivableManagement.dto.UserDTO;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;

@Service
public class CurrentUserService {

    public UserDTO getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }

        Object principal = authentication.getPrincipal();

        if (!(principal instanceof Jwt jwt)) {
            throw new IllegalStateException("Authenticated user is not a JWT principal");
        }

        String userId = jwt.getClaimAsString("user_id");

        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("User ID is missing from JWT");
        }

        return new UserDTO(
                Long.valueOf(userId),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"),
                jwt.getClaimAsStringList("roles"),
                jwt.getClaimAsStringList("permissions")
        );
    }

    public Long getUserId() {
        return getCurrentUser().getId();
    }

    public String getEmail() {
        return getCurrentUser().getEmail();
    }

    public String getName() {
        return getCurrentUser().getName();
    }
}
