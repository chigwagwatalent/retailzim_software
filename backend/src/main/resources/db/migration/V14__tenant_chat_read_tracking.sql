ALTER TABLE tenant_chat_messages
    ADD COLUMN read_by_platform BOOLEAN NOT NULL DEFAULT FALSE AFTER message,
    ADD COLUMN read_by_shop BOOLEAN NOT NULL DEFAULT FALSE AFTER read_by_platform;

UPDATE tenant_chat_messages
SET read_by_platform = CASE WHEN sender_type = 'PLATFORM' THEN TRUE ELSE FALSE END,
    read_by_shop = CASE WHEN sender_type = 'SHOP' THEN TRUE ELSE FALSE END;
