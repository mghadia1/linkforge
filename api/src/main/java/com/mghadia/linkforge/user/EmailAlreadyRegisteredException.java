package com.mghadia.linkforge.user;

/**
 * Registration reports a taken address rather than pretending to succeed.
 *
 * <p>This is a deliberate trade, not an oversight: it tells an attacker which addresses have
 * accounts. The alternative — accepting the registration and sending a "you already have an
 * account" email — needs an email pipeline that does not exist here, and silently doing nothing
 * would leave a real user staring at a form that appears to work but never logs them in. Login is
 * where enumeration is properly defended; see {@link UserService#authenticate}.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("that email address is already registered");
    }
}
