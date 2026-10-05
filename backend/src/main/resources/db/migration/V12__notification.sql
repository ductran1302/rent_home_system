-- Thong bao trong ung dung (in-app), chong trung lap moi su kien theo tung user
CREATE TABLE notification (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES user_account(id),
    type       VARCHAR(40) NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       VARCHAR(500),
    link       VARCHAR(200),
    dedup_key  VARCHAR(200) NOT NULL,
    is_read    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, dedup_key)
);
CREATE INDEX idx_notification_user_created ON notification(user_id, created_at DESC);
CREATE INDEX idx_notification_user_unread ON notification(user_id, is_read);
