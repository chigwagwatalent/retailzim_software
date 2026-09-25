CREATE INDEX idx_chat_tenant_cursor ON tenant_chat_messages(tenant_id, id);
CREATE INDEX idx_chat_platform_unread ON tenant_chat_messages(sender_type, read_by_platform, tenant_id);
CREATE INDEX idx_chat_shop_unread ON tenant_chat_messages(tenant_id, sender_type, read_by_shop);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, is_read);
ALTER TABLE tenant_chat_messages ADD COLUMN client_message_id VARCHAR(36) NULL;
CREATE UNIQUE INDEX uk_chat_retry ON tenant_chat_messages(tenant_id, sender_type, client_message_id);
