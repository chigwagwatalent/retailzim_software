-- Align legacy purchasing tables with the current JPA model while preserving old data.
-- The original schema used po_id / quantity_ordered / unit_price_*; the app now writes
-- purchase_order_id / quantity / unit_cost_*.

SET @schema_name = DATABASE();

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'purchase_orders' AND column_name = 'subtotal_usd'
);
SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE purchase_orders ADD COLUMN subtotal_usd DECIMAL(15,2) NULL DEFAULT 0.00',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'purchase_orders' AND column_name = 'tax_amount_usd'
);
SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE purchase_orders ADD COLUMN tax_amount_usd DECIMAL(15,2) NULL DEFAULT 0.00',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'purchase_orders' AND column_name = 'total_usd'
);
SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE purchase_orders ADD COLUMN total_usd DECIMAL(15,2) NULL DEFAULT 0.00',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'purchase_orders' AND column_name = 'subtotal_zwg'
);
SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE purchase_orders ADD COLUMN subtotal_zwg DECIMAL(15,2) NULL DEFAULT 0.00',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'purchase_orders' AND column_name = 'total_zwg'
);
SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE purchase_orders ADD COLUMN total_zwg DECIMAL(15,2) NULL DEFAULT 0.00',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'purchase_orders' AND column_name = 'sent_to_supplier_at'
);
SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE purchase_orders ADD COLUMN sent_to_supplier_at DATETIME(6) NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE purchase_orders
SET subtotal_usd = COALESCE(subtotal_usd, total_amount, 0.00),
    tax_amount_usd = COALESCE(tax_amount_usd, 0.00),
    total_usd = COALESCE(total_usd, total_amount, subtotal_usd, 0.00),
    subtotal_zwg = COALESCE(subtotal_zwg, 0.00),
    total_zwg = COALESCE(total_zwg, 0.00);

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'purchase_order_items' AND column_name = 'purchase_order_id'
);
SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE purchase_order_items ADD COLUMN purchase_order_id BIGINT NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'purchase_order_items' AND column_name = 'quantity'
);
SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE purchase_order_items ADD COLUMN quantity DECIMAL(15,4) NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'purchase_order_items' AND column_name = 'unit_cost_usd'
);
SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE purchase_order_items ADD COLUMN unit_cost_usd DECIMAL(15,4) NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'purchase_order_items' AND column_name = 'unit_cost_zwg'
);
SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE purchase_order_items ADD COLUMN unit_cost_zwg DECIMAL(15,4) NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'purchase_order_items' AND column_name = 'tax_rate'
);
SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE purchase_order_items ADD COLUMN tax_rate DECIMAL(5,2) NULL DEFAULT 0.00',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'purchase_order_items' AND column_name = 'line_total_zwg'
);
SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE purchase_order_items ADD COLUMN line_total_zwg DECIMAL(15,2) NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'purchase_order_items' AND column_name = 'notes'
);
SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE purchase_order_items ADD COLUMN notes VARCHAR(255) NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE purchase_order_items
SET purchase_order_id = COALESCE(purchase_order_id, po_id),
    quantity = COALESCE(quantity, quantity_ordered),
    unit_cost_usd = COALESCE(unit_cost_usd, unit_price_usd),
    unit_cost_zwg = COALESCE(unit_cost_zwg, unit_price_zwg),
    tax_rate = COALESCE(tax_rate, 0.00),
    line_total_zwg = COALESCE(line_total_zwg, quantity_ordered * unit_price_zwg);

ALTER TABLE purchase_order_items
    MODIFY po_id BIGINT NULL,
    MODIFY quantity_ordered DECIMAL(14,4) NULL,
    MODIFY unit_price_usd DECIMAL(14,4) NULL,
    MODIFY unit_price_zwg DECIMAL(14,4) NULL DEFAULT 0.0000,
    MODIFY line_total_usd DECIMAL(15,2) NULL,
    MODIFY purchase_order_id BIGINT NOT NULL,
    MODIFY quantity DECIMAL(15,4) NOT NULL,
    MODIFY quantity_received DECIMAL(15,4) NULL DEFAULT 0.0000,
    MODIFY unit_cost_usd DECIMAL(15,4) NULL,
    MODIFY unit_cost_zwg DECIMAL(15,4) NULL,
    MODIFY tax_rate DECIMAL(5,2) NULL DEFAULT 0.00,
    MODIFY line_total_zwg DECIMAL(15,2) NULL;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'goods_received_notes' AND column_name = 'purchase_order_id'
);
SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE goods_received_notes ADD COLUMN purchase_order_id BIGINT NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE goods_received_notes
SET purchase_order_id = COALESCE(purchase_order_id, po_id);

ALTER TABLE goods_received_notes
    MODIFY po_id BIGINT NULL,
    MODIFY purchase_order_id BIGINT NOT NULL;
