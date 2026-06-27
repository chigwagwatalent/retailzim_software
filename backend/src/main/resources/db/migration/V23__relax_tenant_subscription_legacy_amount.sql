SET @schema_name = DATABASE();

SET @amount_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'tenant_subscriptions'
      AND column_name = 'amount'
);

SET @ddl = IF(@amount_exists = 1,
    'ALTER TABLE tenant_subscriptions MODIFY COLUMN amount DECIMAL(15,2) NOT NULL DEFAULT 0.00',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE tenant_subscriptions
SET amount = COALESCE(amount, amount_paid, 0.00)
WHERE @amount_exists = 1;

UPDATE tenant_subscriptions
SET amount_paid = COALESCE(amount_paid, amount, 0.00);
