package com.mghadia.linkforge.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret HMAC signing key, base64 or raw text, at least 32 bytes. Left blank, a random key
 *               is generated at startup — see {@code SecurityConfig}. There is deliberately no
 *               default value: a signing key shipped in source is a signing key every deployment
 *               shares, and anyone who reads the repository can mint tokens for any account.
 * @param issuer the {@code iss} claim, checked on every incoming token
 * @param ttl    how long an access token is valid
 */
@ConfigurationProperties(prefix = "linkforge.auth")
public record AuthProperties(String secret, String issuer, Duration ttl) {

    public AuthProperties {
        issuer = issuer == null || issuer.isBlank() ? "linkforge" : issuer;
        ttl = ttl == null ? Duration.ofHours(1) : ttl;
    }
}
