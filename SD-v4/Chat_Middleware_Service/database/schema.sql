CREATE DATABASE IF NOT EXISTS chat_distribuido
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE chat_distribuido;

CREATE TABLE IF NOT EXISTS mensagens (
    message_id CHAR(36) NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    sender VARCHAR(120) NOT NULL,
    recipient VARCHAR(120) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    mode VARCHAR(32) NOT NULL DEFAULT '',
    color VARCHAR(16) NOT NULL DEFAULT '',
    avatar VARCHAR(255) NOT NULL DEFAULT '',
    sticker VARCHAR(255) NOT NULL DEFAULT '',
    sent_at DATETIME(6) NOT NULL,
    metadata JSON NULL,
    rendered_html MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (message_id),
    INDEX idx_mensagens_sent_at (sent_at),
    INDEX idx_mensagens_recipient_sent_at (recipient, sent_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
