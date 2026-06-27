-- Align legacy tenant_subscriptions columns with the JPA model.
-- MySQL variants differ on ADD COLUMN IF NOT EXISTS, so use dynamic checks.

SET @schema_name = DATABASE();

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'tenant_subscriptions'
      AND column_name = 'starts_at'
);
SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE tenant_subscriptions ADD COLUMN starts_at DATETIME(6) NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'tenant_subscriptions'
      AND column_name = 'ends_at'
);
SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE tenant_subscriptions ADD COLUMN ends_at DATETIME(6) NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'tenant_subscriptions'
      AND column_name = 'amount_paid'
);
SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE tenant_subscriptions ADD COLUMN amount_paid DECIMAL(15,2) NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'tenant_subscriptions'
      AND column_name = 'created_by'
);
SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE tenant_subscriptions ADD COLUMN created_by BIGINT NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE tenant_subscriptions
SET starts_at = COALESCE(
        starts_at,
        CASE
            WHEN start_date IS NULL OR CAST(start_date AS CHAR) = '0000-00-00' THEN NOW(6)
            ELSE TIMESTAMP(start_date)
        END
    ),
    ends_at = COALESCE(
        ends_at,
        CASE
            WHEN end_date IS NULL OR CAST(end_date AS CHAR) = '0000-00-00' THEN NULL
            ELSE TIMESTAMP(end_date)
        END
    ),
    amount_paid = COALESCE(amount_paid, amount);

UPDATE tenant_subscriptions
SET status = 'ACTIVE'
WHERE status IS NULL OR status = '';

ALTER TABLE tenant_subscriptions
    MODIFY starts_at DATETIME(6) NOT NULL,
    MODIFY status ENUM('ACTIVE','EXPIRED','CANCELLED','TRIAL') NOT NULL DEFAULT 'ACTIVE',
    MODIFY currency ENUM('USD','ZWG') NOT NULL DEFAULT 'USD';
