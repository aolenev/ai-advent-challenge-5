CREATE TABLE IF NOT EXISTS messages
(
    id              BIGSERIAL PRIMARY KEY,
    message_content TEXT      NOT NULL,
    chat_id         TEXT      NOT NULL,
    message_type    TEXT      NOT NULL,
    created_at      TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS messages_chat_id_idx
    ON messages (chat_id);

CREATE INDEX IF NOT EXISTS messages_created_idx
    ON messages (created_at NULLS LAST);

CREATE TABLE IF NOT EXISTS chats
(
    id      TEXT PRIMARY KEY,
    ai_role TEXT NOT NULL
);