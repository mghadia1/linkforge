CREATE TABLE users (
    id            BIGSERIAL   PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    -- BCrypt output is 60 characters; the column has room for a longer hash if the encoder is
    -- ever upgraded, since old hashes must keep verifying after a change.
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Uniqueness is on the lowercased address, so Alice@example.com and alice@example.com cannot
-- become two accounts. Addresses are stored lowercased as well; the index is the backstop.
CREATE UNIQUE INDEX users_email_key ON users (lower(email));

-- Nullable on purpose: links created before authentication existed have no owner. They still
-- redirect, and they are invisible to every per-user endpoint, which is the correct outcome —
-- inventing an owner for them would be worse than leaving them orphaned.
ALTER TABLE links ADD COLUMN user_id BIGINT REFERENCES users (id);

-- The per-user list is ordered newest first.
CREATE INDEX links_user_id_created_at_idx ON links (user_id, created_at DESC);
