package com.mghadia.linkforge.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private static final int MINIMUM_SECRET_BYTES = 32;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // No cookies and no session are involved: the credential is an Authorization
                // header the browser never attaches on its own, so a cross-site request cannot
                // ride on an existing login. That is what CSRF protection defends, and it is why
                // disabling it here is correct rather than convenient. Move to cookie-based auth
                // and this line has to come back.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        // The production image packages the React build into Spring Boot. Only
                        // these static entry points are public; API authorization is unchanged.
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico",
                                "/assets/**").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        // The redirect is the public product; it must never require a token.
                        .requestMatchers(HttpMethod.GET, "/{code:[0-9A-Za-z]{1,16}}").permitAll()
                        // Health has to answer for load balancers and container probes.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Everything else — including /actuator/metrics, which was open before
                        // this step — needs a valid token.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                }))
                .build();
    }

    /**
     * BCrypt at the default strength. The point of an adaptive hash is that verifying a password
     * is deliberately slow, so a stolen database cannot be turned into a list of passwords at any
     * useful rate.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * One key bean, shared by the encoder and the decoder. It has to be a bean rather than a
     * helper call from each: when no secret is configured the key is randomly generated, and
     * calling a helper twice would generate two different keys, so nothing this service issued
     * would validate against what it reads.
     */
    @Bean
    public SecretKeySpec jwtSigningKey(AuthProperties properties) {
        return signingKey(properties);
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKeySpec signingKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKeySpec signingKey, AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Expiry is checked by default; the issuer check has to be asked for. Without it, a token
        // signed by anything else that shares this key would be accepted.
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    private SecretKeySpec signingKey(AuthProperties properties) {
        String secret = properties.secret();
        if (secret == null || secret.isBlank()) {
            // Never fall back to a fixed value. A random key means tokens do not survive a
            // restart, which is a visible nuisance in development and a loud signal that the
            // secret has not been configured — both far better than a shared default that quietly
            // works everywhere, including for anyone who has read this file.
            byte[] generated = new byte[MINIMUM_SECRET_BYTES];
            new SecureRandom().nextBytes(generated);
            log.warn("linkforge.auth.secret is not set — generating a random signing key. "
                    + "Every restart invalidates all issued tokens. Set LINKFORGE_JWT_SECRET "
                    + "(32+ bytes) before deploying this anywhere.");
            return new SecretKeySpec(generated, "HmacSHA256");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MINIMUM_SECRET_BYTES) {
            // HS256 with a short key is brute-forceable offline: an attacker with one token can
            // recover the key and mint tokens for any account. Refusing to start is the only
            // sane response.
            throw new IllegalStateException("linkforge.auth.secret must be at least "
                    + MINIMUM_SECRET_BYTES + " bytes, got " + bytes.length);
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
