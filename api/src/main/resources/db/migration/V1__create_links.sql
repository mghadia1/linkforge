-- Short codes are base62 encodings of values drawn from this sequence.
-- Using a sequence (rather than random codes) means a code is unique by
-- construction: no collision check, no retry loop on the write path.
CREATE SEQUENCE link_code_seq START WITH 1000000 INCREMENT BY 1;

CREATE TABLE links (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(16)  NOT NULL,
    long_url    TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- The redirect path looks a link up by code on every request, so this index
-- is the one that matters for read performance.
CREATE UNIQUE INDEX links_code_key ON links (code);
