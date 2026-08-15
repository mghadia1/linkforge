package com.mghadia.linkforge.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Addresses are stored lowercased, so callers must lowercase before looking one up. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
