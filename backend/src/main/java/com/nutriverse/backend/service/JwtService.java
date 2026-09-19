package com.nutriverse.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {
    private static final long EXPIRATION = 1000L * 60 * 60 * 24;
    private final SecretKey key;

    public JwtService(@Value("${JWT_SECRET}") String secret) {
        if (secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32
                || secret.startsWith("change-this")) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes of random secret material");
        }
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String userId, String username, String role) {
        return Jwts.builder().subject(username).claim("userId", userId).claim("role", role)
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(key).compact();
    }

    public Claims authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) throw unauthorized();
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(authorizationHeader.substring(7).trim()).getPayload();
            String userId = claims.get("userId", String.class);
            if (userId == null || userId.isBlank() || claims.getExpiration() == null
                    || !claims.getExpiration().after(new Date())) throw unauthorized();
            return claims;
        } catch (JwtException | IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired authentication token");
    }
}
