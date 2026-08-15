package com.mghadia.linkforge.click;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The link is held as a plain id rather than a {@code @ManyToOne}: the write path already knows
 * the id, and an association would make Hibernate load the link again on every insert.
 */
@Entity
@Table(name = "clicks")
public class Click {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "link_id", nullable = false)
    private Long linkId;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    @Column(name = "referrer_host", length = 255)
    private String referrerHost;

    @Column(name = "ip_anonymized", length = 45)
    private String ipAnonymized;

    protected Click() {
        // for JPA
    }

    public Click(ClickEvent event) {
        this.linkId = event.linkId();
        this.clickedAt = event.clickedAt();
        this.referrerHost = event.referrerHost();
        this.ipAnonymized = event.ipAnonymized();
    }

    public Long getId() {
        return id;
    }

    public Long getLinkId() {
        return linkId;
    }

    public Instant getClickedAt() {
        return clickedAt;
    }

    public String getReferrerHost() {
        return referrerHost;
    }

    public String getIpAnonymized() {
        return ipAnonymized;
    }
}
