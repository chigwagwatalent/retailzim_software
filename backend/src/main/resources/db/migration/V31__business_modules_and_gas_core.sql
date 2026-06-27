DROP PROCEDURE IF EXISTS add_column_if_missing;

DELIMITER //
CREATE PROCEDURE add_column_if_missing(IN table_name_value VARCHAR(64), IN column_name_value VARCHAR(64), IN ddl_value TEXT)
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

CALL add_column_if_missing('tenants', 'business_mode',
  'ALTER TABLE tenants ADD COLUMN business_mode VARCHAR(30) NOT NULL DEFAULT ''SINGLE_MODULE''');
CALL add_column_if_missing('branches', 'module_type',
  'ALTER TABLE branches ADD COLUMN module_type VARCHAR(30) NOT NULL DEFAULT ''SHOP_MODULE''');
CALL add_column_if_missing('saas_plans', 'max_gas_tanks',
  'ALTER TABLE saas_plans ADD COLUMN max_gas_tanks INT DEFAULT 0');
CALL add_column_if_missing('saas_plans', 'allowed_modules',
  'ALTER TABLE saas_plans ADD COLUMN allowed_modules VARCHAR(255) NOT NULL DEFAULT ''SHOP_MODULE''');
CALL add_column_if_missing('saas_plans', 'allow_mixed_modules',
  'ALTER TABLE saas_plans ADD COLUMN allow_mixed_modules BIT(1) DEFAULT b''0''');
CALL add_column_if_missing('saas_plans', 'gas_reconciliation_enabled',
  'ALTER TABLE saas_plans ADD COLUMN gas_reconciliation_enabled BIT(1) DEFAULT b''0''');

DROP PROCEDURE add_column_if_missing;

UPDATE saas_plans SET allowed_modules = 'SHOP_MODULE' WHERE allowed_modules IS NULL OR allowed_modules = '';
UPDATE tenants SET business_mode = 'SINGLE_MODULE' WHERE business_mode IS NULL OR business_mode = '';
UPDATE branches SET module_type = 'SHOP_MODULE' WHERE module_type IS NULL OR module_type = '';

CREATE TABLE IF NOT EXISTS tenant_enabled_modules (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  module VARCHAR(30) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME(6),
  updated_at DATETIME(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_enabled_module (tenant_id, module),
  KEY idx_tenant_enabled_modules_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO tenant_enabled_modules (tenant_id, module, status, created_at, updated_at)
SELECT id, 'SHOP_MODULE', 'ENABLED', NOW(6), NOW(6) FROM tenants;

INSERT IGNORE INTO tenant_enabled_modules (tenant_id, module, status, created_at, updated_at)
SELECT t.id, 'GAS_MODULE', 'ENABLED', NOW(6), NOW(6)
FROM tenants t
JOIN saas_plans p ON p.id = t.plan_id
WHERE p.allowed_modules LIKE '%GAS_MODULE%';

UPDATE tenants t
SET business_mode = 'MIXED_MODULE'
WHERE EXISTS (
  SELECT 1 FROM tenant_enabled_modules m1
  WHERE m1.tenant_id = t.id AND m1.module = 'SHOP_MODULE' AND m1.status = 'ENABLED'
)
AND EXISTS (
  SELECT 1 FROM tenant_enabled_modules m2
  WHERE m2.tenant_id = t.id AND m2.module = 'GAS_MODULE' AND m2.status = 'ENABLED'
);

CREATE TABLE IF NOT EXISTS gas_tanks (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  name VARCHAR(80) NOT NULL,
  product_name VARCHAR(120) NOT NULL,
  capacity_kg DECIMAL(12,3),
  current_kg DECIMAL(12,3) NOT NULL DEFAULT 0,
  reorder_level_kg DECIMAL(12,3) DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6),
  updated_at DATETIME(6),
  PRIMARY KEY (id),
  KEY idx_gas_tanks_branch (tenant_id, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gas_prices (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  currency VARCHAR(5) NOT NULL,
  price_per_kg DECIMAL(12,4) NOT NULL,
  is_active BIT(1) DEFAULT b'1',
  created_at DATETIME(6),
  PRIMARY KEY (id),
  KEY idx_gas_prices_branch_currency (tenant_id, branch_id, currency, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gas_shifts (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  cashier_id BIGINT NOT NULL,
  shift_number VARCHAR(50) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  opened_at DATETIME(6) NOT NULL,
  closed_at DATETIME(6),
  total_kg_sold DECIMAL(12,3) DEFAULT 0,
  total_usd DECIMAL(12,2) DEFAULT 0,
  total_zwg DECIMAL(12,2) DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_gas_shifts_open (tenant_id, branch_id, cashier_id, status),
  KEY idx_gas_shifts_branch (tenant_id, branch_id, opened_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gas_sales (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  gas_shift_id BIGINT NOT NULL,
  tank_id BIGINT NOT NULL,
  cashier_id BIGINT NOT NULL,
  receipt_number VARCHAR(80) NOT NULL,
  customer_name VARCHAR(120),
  customer_phone VARCHAR(30),
  quantity_kg DECIMAL(12,3) NOT NULL,
  unit_price DECIMAL(12,4) NOT NULL,
  total DECIMAL(12,2) NOT NULL,
  currency VARCHAR(5) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
  offline_receipt_number VARCHAR(100),
  created_at DATETIME(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_gas_sale_receipt (tenant_id, receipt_number),
  UNIQUE KEY uk_gas_sale_offline_receipt (tenant_id, offline_receipt_number),
  KEY idx_gas_sales_shift (tenant_id, branch_id, gas_shift_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gas_restocks (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  tank_id BIGINT NOT NULL,
  quantity_kg DECIMAL(12,3) NOT NULL,
  supplier_name VARCHAR(120),
  notes VARCHAR(500),
  created_by BIGINT,
  created_at DATETIME(6),
  PRIMARY KEY (id),
  KEY idx_gas_restocks_branch (tenant_id, branch_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
