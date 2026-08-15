package com.mghadia.linkforge.user;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final Clock clock;

    /**
     * A hash of a random value nobody will ever submit, used to spend the same CPU time verifying
     * a password for an address that does not exist as for one that does. Without it, "no such
     * user" returns in microseconds while a real address pays the full hashing cost, and the
     * difference is measurable from outside — which makes login an account-enumeration oracle
     * however carefully the error messages are worded.
     */
    private final String dummyHash;

    public UserService(UserRepository users, PasswordEncoder passwords, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.clock = clock;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public User register(String email, String rawPassword) {
        String normalized = normalize(email);
        if (users.existsByEmail(normalized)) {
            throw new EmailAlreadyRegisteredException();
        }
        try {
            return users.save(new User(normalized, passwords.encode(rawPassword), clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // Two concurrent registrations of the same address: the unique index is what actually
            // decides, and the check above is only a fast path.
            throw new EmailAlreadyRegisteredException();
        }
    }

    /**
     * @return the user, or empty for both an unknown address and a wrong password — the caller
     *         must not be able to tell those apart
     */
    @Transactional(readOnly = true)
    public Optional<User> authenticate(String email, String rawPassword) {
        Optional<User> found = users.findByEmail(normalize(email));
        if (found.isEmpty()) {
            passwords.matches(rawPassword, dummyHash);
            return Optional.empty();
        }
        User user = found.get();
        return passwords.matches(rawPassword, user.getPasswordHash()) ? Optional.of(user)
                : Optional.empty();
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
