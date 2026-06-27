DELIMITER $$
CREATE PROCEDURE retailzw_add_column_if_missing(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND column_name = p_column
    ) THEN
        SET @retailzw_ddl = CONCAT(
            'ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition
        );
        PREPARE retailzw_stmt FROM @retailzw_ddl;
        EXECUTE retailzw_stmt;
        DEALLOCATE PREPARE retailzw_stmt;
    END IF;
END$$
DELIMITER ;

CALL retailzw_add_column_if_missing('stock_transfers', 'transfer_number', 'VARCHAR(50)');
CALL retailzw_add_column_if_missing('stock_transfers', 'initiated_by', 'BIGINT');
CALL retailzw_add_column_if_missing('stock_transfers', 'initiated_at', 'DATETIME(6)');
UPDATE stock_transfers SET transfer_number = reference_number WHERE transfer_number IS NULL;
UPDATE stock_transfers SET initiated_by = created_by WHERE initiated_by IS NULL;
UPDATE stock_transfers SET initiated_at = created_at WHERE initiated_at IS NULL;
ALTER TABLE stock_transfers
    MODIFY reference_number VARCHAR(50) NULL,
    MODIFY created_by BIGINT NULL,
    MODIFY transfer_number VARCHAR(50) NOT NULL,
    MODIFY initiated_by BIGINT NOT NULL;

CALL retailzw_add_column_if_missing('stock_transfer_items', 'unit_cost_usd', 'DECIMAL(15,4)');
CALL retailzw_add_column_if_missing('stock_transfer_items', 'unit_cost_zwg', 'DECIMAL(15,4)');
CALL retailzw_add_column_if_missing('stock_transfer_items', 'notes', 'VARCHAR(255)');

CALL retailzw_add_column_if_missing('stocktake_sessions', 'session_number', 'VARCHAR(50)');
CALL retailzw_add_column_if_missing('stocktake_sessions', 'started_by', 'BIGINT');
CALL retailzw_add_column_if_missing('stocktake_sessions', 'started_at', 'DATETIME(6)');
UPDATE stocktake_sessions SET session_number = reference_number WHERE session_number IS NULL;
UPDATE stocktake_sessions SET started_by = created_by WHERE started_by IS NULL;
UPDATE stocktake_sessions SET started_at = created_at WHERE started_at IS NULL;
UPDATE stocktake_sessions SET status = 'OPEN' WHERE status = 'IN_PROGRESS';
UPDATE stocktake_sessions SET status = 'SUBMITTED' WHERE status = 'PENDING_APPROVAL';
ALTER TABLE stocktake_sessions
    MODIFY status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    MODIFY reference_number VARCHAR(50) NULL,
    MODIFY created_by BIGINT NULL,
    MODIFY session_number VARCHAR(50) NOT NULL,
    MODIFY started_by BIGINT NOT NULL;

CALL retailzw_add_column_if_missing('stocktake_items', 'variance_value_usd', 'DECIMAL(15,2)');
CALL retailzw_add_column_if_missing('stocktake_items', 'variance_value_zwg', 'DECIMAL(15,2)');
CALL retailzw_add_column_if_missing('stocktake_items', 'is_counted', 'BOOLEAN NOT NULL DEFAULT FALSE');
CALL retailzw_add_column_if_missing('stocktake_items', 'counted_by', 'BIGINT');
CALL retailzw_add_column_if_missing('stocktake_items', 'counted_at', 'DATETIME(6)');
CALL retailzw_add_column_if_missing('stocktake_items', 'notes', 'VARCHAR(255)');

CALL retailzw_add_column_if_missing('product_suppliers', 'supplier_sku', 'VARCHAR(100)');
CALL retailzw_add_column_if_missing('product_suppliers', 'supplier_product_name', 'VARCHAR(200)');
CALL retailzw_add_column_if_missing('product_suppliers', 'cost_price_usd', 'DECIMAL(15,4)');
CALL retailzw_add_column_if_missing('product_suppliers', 'cost_price_zwg', 'DECIMAL(15,4)');
CALL retailzw_add_column_if_missing('product_suppliers', 'minimum_order_qty', 'DECIMAL(15,4)');
CALL retailzw_add_column_if_missing(
    'product_suppliers',
    'created_at',
    'DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)'
);
UPDATE product_suppliers SET supplier_sku = supplier_product_code WHERE supplier_sku IS NULL;
UPDATE product_suppliers SET cost_price_usd = purchase_price_usd WHERE cost_price_usd IS NULL;
UPDATE product_suppliers SET cost_price_zwg = purchase_price_zwg WHERE cost_price_zwg IS NULL;

ALTER TABLE purchase_orders MODIFY status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';

DROP PROCEDURE retailzw_add_column_if_missing;
