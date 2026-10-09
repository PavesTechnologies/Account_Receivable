package com.AccountReceivableManagement.security;


import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    // Spring's configured mapper (has JavaTimeModule for the LocalDateTime timestamp)
    private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                        AuthenticationException authException) throws IOException, ServletException {

        String origin = request.getHeader("Origin");
        if (origin != null) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (authException.getMessage() != null && authException.getMessage().contains("Jwt expired")) {
            response.getWriter().write("{\"detail\":\"401: Token has expired\"}");
        } else {
            ApiResponse apiResponse = new ApiResponse();
            apiResponse.setSuccess(false);
            apiResponse.setMessage("Authentication failed: " + authException.getMessage());
            apiResponse.setData(null);
            apiResponse.setTimestamp(LocalDateTime.now());
            String jsonResponse = objectMapper.writeValueAsString(apiResponse);
            response.getWriter().write(jsonResponse);
        }
    }
}
