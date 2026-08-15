package com.mghadia.linkforge.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * @param password minimum length only. NIST SP 800-63B advises exactly this — length, no
     *                 composition rules — but it also advises checking candidates against a
     *                 breached-password list, which this does not do. That gap is in the README.
     */
    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 200, message = "password must be at least 8 characters")
            String password) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    /**
     * @param expiresAt returned so a client can refresh before a request fails rather than after
     */
    public record TokenResponse(String token, String tokenType, Instant expiresAt) {

        public static TokenResponse bearer(String token, Instant expiresAt) {
            return new TokenResponse(token, "Bearer", expiresAt);
        }
    }
}
