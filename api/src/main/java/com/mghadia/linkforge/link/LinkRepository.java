package com.mghadia.linkforge.link;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LinkRepository extends JpaRepository<Link, Long> {

    Optional<Link> findByCode(String code);

    Page<Link> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Draws the next value from the code sequence. Postgres sequences hand out distinct values to
     * concurrent callers without locking, which is why the create path needs no collision retry.
     */
    @Query(value = "SELECT nextval('link_code_seq')", nativeQuery = true)
    long nextCodeValue();
}
