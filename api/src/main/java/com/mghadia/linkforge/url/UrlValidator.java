package com.mghadia.linkforge.url;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * Accepts only URLs this service is willing to redirect to.
 *
 * <p>A shortener is an open-redirect engine by definition, so the scheme allowlist is the
 * security boundary: without it a stored {@code javascript:} or {@code file:} target turns
 * every short link into an attack. Blocking private and loopback destinations (SSRF) is a
 * separate concern and is deliberately left to the hardening step.
 */
public final class UrlValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final int MAX_LENGTH = 2048;

    private UrlValidator() {
    }

    /**
     * @return the normalized URL to store
     * @throws InvalidUrlException if the input is not a URL this service will redirect to
     */
    public static String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidUrlException("url must not be blank");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new InvalidUrlException("url must be at most " + MAX_LENGTH + " characters");
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("url is not a valid URI: " + e.getReason());
        }

        if (!uri.isAbsolute() || uri.getScheme() == null) {
            throw new InvalidUrlException("url must be absolute and include a scheme");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new InvalidUrlException("url scheme must be http or https, got " + scheme);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("url must include a host");
        }
        if (uri.getUserInfo() != null) {
            // user:pass@host is a classic phishing disguise in a redirect target.
            throw new InvalidUrlException("url must not contain user info");
        }
        return trimmed;
    }
}
