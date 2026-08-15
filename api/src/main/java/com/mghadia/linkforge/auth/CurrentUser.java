package com.mghadia.linkforge.auth;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads the caller's id out of a validated token.
 *
 * <p>By the time a {@link Jwt} reaches a controller the resource server has already checked its
 * signature, expiry and issuer, so the subject can be trusted. Nothing in this codebase may take a
 * user id from a request body or a query parameter — that is the difference between an
 * authenticated system and one that asks the caller who they are.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static long idOf(Jwt token) {
        try {
            return Long.parseLong(token.getSubject());
        } catch (NumberFormatException e) {
            // Only reachable with a token this service signed but did not issue, which would mean
            // the signing key had leaked. Fail rather than guess.
            throw new IllegalStateException("token subject is not a user id");
        }
    }
}
