DROP PROCEDURE IF EXISTS retailzw_add_column_if_missing;

DELIMITER $$
CREATE PROCEDURE retailzw_add_column_if_missing(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN ddl_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND column_name = column_name_value
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE ', table_name_value, ' ADD COLUMN ', column_name_value, ' ', ddl_value);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL retailzw_add_column_if_missing('return_items', 'product_name', 'VARCHAR(200) NULL AFTER product_id');
CALL retailzw_add_column_if_missing('return_items', 'restock_item', 'BOOLEAN NOT NULL DEFAULT TRUE AFTER refund_amount');
CALL retailzw_add_column_if_missing('return_items', 'notes', 'VARCHAR(255) NULL AFTER restock_item');

UPDATE return_items
SET product_name = CONCAT('Product #', product_id)
WHERE product_name IS NULL OR product_name = '';

ALTER TABLE return_items
    MODIFY quantity_returned DECIMAL(15,4) NOT NULL,
    MODIFY unit_price DECIMAL(15,4) NOT NULL,
    MODIFY refund_amount DECIMAL(15,2) NOT NULL,
    MODIFY product_name VARCHAR(200) NOT NULL,
    MODIFY restock_item BOOLEAN NOT NULL DEFAULT TRUE;

DROP PROCEDURE retailzw_add_column_if_missing;
