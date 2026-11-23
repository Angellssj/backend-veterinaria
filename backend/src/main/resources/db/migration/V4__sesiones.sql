ALTER TABLE products MODIFY COLUMN image_url VARCHAR(1000);

CREATE TABLE login_sessions (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    refresh_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_sessions_user ON login_sessions (user_id);
