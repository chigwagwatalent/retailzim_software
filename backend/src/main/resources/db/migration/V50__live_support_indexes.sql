CREATE INDEX idx_chat_tenant_cursor ON tenant_chat_messages(tenant_id, id);
CREATE INDEX idx_chat_platform_unread ON tenant_chat_messages(sender_type, read_by_platform, tenant_id);
CREATE INDEX idx_chat_shop_unread ON tenant_chat_messages(tenant_id, sender_type, read_by_shop);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, is_read);
SET @migration_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tenant_chat_messages' AND column_name = 'client_message_id') = 0, 'ALTER TABLE tenant_chat_messages ADD COLUMN client_message_id VARCHAR(36) NULL', 'SELECT 1');
PREPARE migration_stmt FROM @migration_sql;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
CREATE UNIQUE INDEX uk_chat_retry ON tenant_chat_messages(tenant_id, sender_type, client_message_id);
