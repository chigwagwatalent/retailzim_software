CREATE TABLE IF NOT EXISTS gas_stock_adjustments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  tank_id BIGINT NOT NULL,
  quantity_before_kg DECIMAL(12,3) NOT NULL,
  counted_kg DECIMAL(12,3) NOT NULL,
  variance_kg DECIMAL(12,3) NOT NULL,
  reason VARCHAR(40) NOT NULL,
  notes VARCHAR(500),
  created_by BIGINT,
  created_at DATETIME(6),
  PRIMARY KEY (id),
  KEY idx_gas_adjustments_branch (tenant_id, branch_id, created_at),
  KEY idx_gas_adjustments_tank (tenant_id, tank_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
