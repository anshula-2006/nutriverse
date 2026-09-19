package com.nutriverse.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {
@NotBlank
    @Size(max = 30)
    private String username;
@NotBlank
    @Size(max = 72)
    private String password;

        public String getUsername() {
    return username;
        }
        public String getPassword() {
    return password;
        }
        public void setUsername(String username) {
    this.username = username;
        }
        public void setPassword(String password) {
    this.password = password;
        }
}
