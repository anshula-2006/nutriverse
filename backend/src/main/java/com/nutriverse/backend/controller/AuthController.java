package com.nutriverse.backend.controller;

import com.nutriverse.backend.dto.LoginRequest;
import com.nutriverse.backend.dto.RegisterRequest;
import com.nutriverse.backend.model.User;
import com.nutriverse.backend.repository.UserRepository;
import com.nutriverse.backend.service.JwtService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MongoTemplate mongoTemplate;

    public AuthController(
            UserRepository userRepository,
            JwtService jwtService,
            MongoTemplate mongoTemplate) {

        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.mongoTemplate = mongoTemplate;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest request) {

        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            return ResponseEntity.badRequest().body(Map.of("message", "Password must not exceed 72 UTF-8 bytes"));
        }

        // Idempotent and lazy: enforce uniqueness across concurrent registrations
        // without making application startup depend on a database connection.
        mongoTemplate.indexOps(User.class).createIndex(new Index().on("username", Sort.Direction.ASC).unique());
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username already exists"));
        }

        String hashedPassword =
                passwordEncoder.encode(request.getPassword());

        User user = new User(
                request.getName(),
                request.getUsername(),
                hashedPassword
        );

        User savedUser;
        try {
            savedUser = userRepository.save(user);
        } catch (DuplicateKeyException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username already exists"));
        }

        return ResponseEntity.ok(
                Map.of(
                        "message", "Account created successfully",
                        "username", savedUser.getUsername(),
                        "id", savedUser.getId()
                )
        );
    }


    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request) {

        User user = userRepository
                .findByUsername(request.getUsername())
                .orElse(null);

        if (user == null || request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message",
                            "Invalid username or password"
                    ));
        }

        boolean passwordMatches =
                passwordEncoder.matches(
                        request.getPassword(),
                        user.getPasswordHash()
                );

        if (!passwordMatches) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message",
                            "Invalid username or password"
                    ));
        }

        String token = jwtService.generateToken(
                user.getId(),
                user.getUsername(),
                user.getRole()
        );

        return ResponseEntity.ok(
                Map.of(
                        "message", "Login successful",
                        "token", token,
                        "userId", user.getId(),
                        "username", user.getUsername(),
                        "name", user.getName(),
                        "role", user.getRole()
                )
        );
    }
}
