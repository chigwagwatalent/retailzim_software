DROP PROCEDURE IF EXISTS retailzw_modify_column_if_exists;

DELIMITER $$
CREATE PROCEDURE retailzw_modify_column_if_exists(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN ddl_value TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND column_name = column_name_value
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE ', table_name_value, ' MODIFY COLUMN ', ddl_value);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL retailzw_modify_column_if_exists(
    'return_items',
    'quantity',
    'quantity DECIMAL(15,4) NULL DEFAULT NULL'
);

DROP PROCEDURE retailzw_modify_column_if_exists;
