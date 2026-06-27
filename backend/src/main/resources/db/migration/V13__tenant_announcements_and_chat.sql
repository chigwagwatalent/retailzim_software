CREATE TABLE IF NOT EXISTS tenant_announcements (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    subject VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    audience_mode VARCHAR(20) NOT NULL,
    audience_summary VARCHAR(500),
    recipient_count INT DEFAULT 0,
    email_enabled BOOLEAN DEFAULT FALSE,
    email_sent_count INT DEFAULT 0,
    notification_sent_count INT DEFAULT 0,
    created_by VARCHAR(100),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tenant_chat_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    sender_type VARCHAR(20) NOT NULL,
    sender_name VARCHAR(120) NOT NULL,
    message TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_chat_tenant_created (tenant_id, created_at)
);
