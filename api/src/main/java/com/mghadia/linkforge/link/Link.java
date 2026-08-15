package com.mghadia.linkforge.link;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "links")
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(name = "long_url", nullable = false)
    private String longUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Null only for links created before authentication existed; see V3__create_users.sql. */
    @Column(name = "user_id")
    private Long userId;

    protected Link() {
        // for JPA
    }

    public Link(String code, String longUrl, Long userId, Instant createdAt) {
        this.code = code;
        this.longUrl = longUrl;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isOwnedBy(long candidate) {
        return userId != null && userId == candidate;
    }
}
