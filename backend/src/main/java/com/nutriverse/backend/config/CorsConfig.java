package com.nutriverse.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    private final JwtAuthenticationInterceptor authentication;
    private final String[] allowedOrigins;

    public CorsConfig(JwtAuthenticationInterceptor authentication,
                      @Value("${app.cors.allowed-origins:http://localhost:5173}") String[] allowedOrigins) {
        this.authentication = authentication;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authentication).addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/register");
    }
}
