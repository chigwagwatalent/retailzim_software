DROP PROCEDURE IF EXISTS retailzw_add_column_if_missing;

DELIMITER //
CREATE PROCEDURE retailzw_add_column_if_missing(IN table_name_value VARCHAR(64), IN column_name_value VARCHAR(64), IN ddl_value TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = table_name_value
      AND column_name = column_name_value
  ) THEN
    SET @ddl = ddl_value;
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END//
DELIMITER ;

CALL retailzw_add_column_if_missing('gas_sales', 'payment_method',
  'ALTER TABLE gas_sales ADD COLUMN payment_method VARCHAR(30) NOT NULL DEFAULT ''CASH'' AFTER currency');
CALL retailzw_add_column_if_missing('gas_sales', 'payment_reference',
  'ALTER TABLE gas_sales ADD COLUMN payment_reference VARCHAR(120) NULL AFTER payment_method');

CALL retailzw_add_column_if_missing('gas_restocks', 'currency',
  'ALTER TABLE gas_restocks ADD COLUMN currency VARCHAR(5) NOT NULL DEFAULT ''USD'' AFTER quantity_kg');
CALL retailzw_add_column_if_missing('gas_restocks', 'unit_cost',
  'ALTER TABLE gas_restocks ADD COLUMN unit_cost DECIMAL(12,4) NOT NULL DEFAULT 0.0000 AFTER currency');
CALL retailzw_add_column_if_missing('gas_restocks', 'total_cost',
  'ALTER TABLE gas_restocks ADD COLUMN total_cost DECIMAL(12,2) NOT NULL DEFAULT 0.00 AFTER unit_cost');
CALL retailzw_add_column_if_missing('gas_restocks', 'supplier_invoice',
  'ALTER TABLE gas_restocks ADD COLUMN supplier_invoice VARCHAR(120) NULL AFTER supplier_name');

CREATE TABLE IF NOT EXISTS gas_expenses (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  category VARCHAR(80) NOT NULL,
  description VARCHAR(255) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  currency VARCHAR(5) NOT NULL,
  payment_method VARCHAR(30) NOT NULL DEFAULT 'CASH',
  reference VARCHAR(120),
  created_by BIGINT,
  created_at DATETIME(6),
  PRIMARY KEY (id),
  KEY idx_gas_expenses_branch (tenant_id, branch_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

UPDATE gas_restocks
SET total_cost = ROUND(quantity_kg * unit_cost, 2)
WHERE total_cost IS NULL OR total_cost = 0.00;

DROP PROCEDURE retailzw_add_column_if_missing;
