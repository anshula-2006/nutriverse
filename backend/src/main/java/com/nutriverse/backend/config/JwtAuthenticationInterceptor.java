package com.nutriverse.backend.config;

import com.nutriverse.backend.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtAuthenticationInterceptor implements HandlerInterceptor {
    private final JwtService jwtService;
    public JwtAuthenticationInterceptor(JwtService jwtService) { this.jwtService = jwtService; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equals(request.getMethod())) return true;
        Claims claims = jwtService.authenticate(request.getHeader("Authorization"));
        request.setAttribute("authenticatedUserId", claims.get("userId", String.class));
        request.setAttribute("authenticatedRole", claims.get("role", String.class));
        return true;
    }
}
