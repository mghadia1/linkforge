package com.mghadia.linkforge.auth;

/** Carries no detail on purpose — the message is returned to the caller verbatim. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("invalid email or password");
    }
}
