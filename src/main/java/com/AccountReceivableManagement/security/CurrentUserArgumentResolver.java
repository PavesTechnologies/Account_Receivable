package com.AccountReceivableManagement.security;

import com.AccountReceivableManagement.dto.UserDTO;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterAnnotation(CurrentUser.class) != null
                && parameter.getParameterType().equals(UserDTO.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {

        Authentication auth = (Authentication) webRequest.getUserPrincipal();

        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }

        return new UserDTO(
                Long.valueOf(jwt.getClaimAsString("user_id")),
                jwt.getClaim("email"),
                jwt.getClaimAsString("name"),
                jwt.getClaimAsStringList("roles"),
                jwt.getClaimAsStringList("permissions")
        );
    }
}