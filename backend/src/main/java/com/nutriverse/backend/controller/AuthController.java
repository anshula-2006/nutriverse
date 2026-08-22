package com.nutriverse.backend.controller;

import com.nutriverse.backend.dto.RegisterRequest;
import com.nutriverse.backend.model.User;
import com.nutriverse.backend.repository.UserRepository;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }
    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest request) {

        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "message", "Username already exists"
                    ));
        }

        String hashedPassword =
                passwordEncoder.encode(request.getPassword());

        User user = new User(
                request.getName(),
                request.getUsername(),
                hashedPassword
        );

        User savedUser = userRepository.save(user);

        long count = userRepository.count();

        return ResponseEntity.ok(
                Map.of(
                        "message", "Account created successfully",
                        "username", savedUser.getUsername(),
                        "id", savedUser.getId(),
                        "userCount", count
                )
        );
    }
    }

