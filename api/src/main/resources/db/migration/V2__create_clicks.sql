-- One row per redirect served. Written off the request thread, so this table is never on the
-- critical path of a redirect.
CREATE TABLE clicks (
    id             BIGSERIAL   PRIMARY KEY,
    link_id        BIGINT      NOT NULL REFERENCES links (id) ON DELETE CASCADE,
    clicked_at     TIMESTAMPTZ NOT NULL,
    -- Host only, not the full referring URL: the dashboard groups by source, and a bare host
    -- avoids storing the query strings of pages that link here.
    referrer_host  VARCHAR(255),
    -- Last octet (IPv4) or everything below the /48 (IPv6) is zeroed before it reaches this
    -- column, so it supports approximate unique-visitor counts without storing a full address.
    ip_anonymized  VARCHAR(45)
);

-- Every stats query filters by link and by time window, in that order.
CREATE INDEX clicks_link_id_clicked_at_idx ON clicks (link_id, clicked_at);
