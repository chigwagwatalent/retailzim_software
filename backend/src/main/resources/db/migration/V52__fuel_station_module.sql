-- Fuel Station is an optional RetailZW business module. Tenant, branch and user
-- identity remain owned by the existing platform; only fuel-domain state lives
-- in these tables.
CREATE TABLE IF NOT EXISTS fuel_grades (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  code VARCHAR(30) NOT NULL,
  name VARCHAR(100) NOT NULL,
  colour VARCHAR(20) NOT NULL DEFAULT '#087cf0',
  active BIT(1) NOT NULL DEFAULT b'1',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_fuel_grade (tenant_id, code),
  KEY idx_fuel_grade_tenant (tenant_id, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fuel_tanks (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  grade_id BIGINT NOT NULL,
  code VARCHAR(30) NOT NULL,
  safe_capacity_l DECIMAL(15,3) NOT NULL,
  book_stock_l DECIMAL(15,3) NOT NULL DEFAULT 0,
  reorder_level_l DECIMAL(15,3) NOT NULL DEFAULT 0,
  latest_dip_l DECIMAL(15,3),
  water_level_mm DECIMAL(10,3) NOT NULL DEFAULT 0,
  average_cost_per_l DECIMAL(15,4) NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  active BIT(1) NOT NULL DEFAULT b'1',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_fuel_tank (branch_id, code),
  KEY idx_fuel_tank_branch (tenant_id, branch_id, active),
  CONSTRAINT fk_fuel_tank_grade FOREIGN KEY (grade_id) REFERENCES fuel_grades(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fuel_pumps (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  code VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'ONLINE',
  controller_ref VARCHAR(100),
  active BIT(1) NOT NULL DEFAULT b'1',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_fuel_pump (branch_id, code),
  KEY idx_fuel_pump_branch (tenant_id, branch_id, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fuel_nozzles (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  pump_id BIGINT NOT NULL,
  tank_id BIGINT NOT NULL,
  grade_id BIGINT NOT NULL,
  code VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'IDLE',
  meter_l DECIMAL(18,3) NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  active BIT(1) NOT NULL DEFAULT b'1',
  PRIMARY KEY (id),
  UNIQUE KEY uk_fuel_nozzle (pump_id, code),
  KEY idx_fuel_nozzle_branch (tenant_id, branch_id, active),
  CONSTRAINT fk_fuel_nozzle_pump FOREIGN KEY (pump_id) REFERENCES fuel_pumps(id),
  CONSTRAINT fk_fuel_nozzle_tank FOREIGN KEY (tank_id) REFERENCES fuel_tanks(id),
  CONSTRAINT fk_fuel_nozzle_grade FOREIGN KEY (grade_id) REFERENCES fuel_grades(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fuel_prices (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  grade_id BIGINT NOT NULL,
  currency VARCHAR(5) NOT NULL,
  amount_per_l DECIMAL(15,4) NOT NULL,
  effective_at DATETIME(6) NOT NULL,
  approved_by BIGINT NOT NULL,
  active BIT(1) NOT NULL DEFAULT b'1',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_fuel_price_lookup (tenant_id, branch_id, grade_id, currency, active, effective_at),
  CONSTRAINT fk_fuel_price_grade FOREIGN KEY (grade_id) REFERENCES fuel_grades(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fuel_shifts (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  shift_no VARCHAR(60) NOT NULL,
  cashier_id BIGINT NOT NULL,
  supervisor_id BIGINT,
  status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
  opened_at DATETIME(6) NOT NULL,
  closed_at DATETIME(6),
  opening_usd DECIMAL(15,2) NOT NULL DEFAULT 0,
  opening_zwg DECIMAL(15,2) NOT NULL DEFAULT 0,
  counted_usd DECIMAL(15,2),
  counted_zwg DECIMAL(15,2),
  expected_usd DECIMAL(15,2),
  expected_zwg DECIMAL(15,2),
  cash_variance_usd DECIMAL(15,2),
  cash_variance_zwg DECIMAL(15,2),
  meter_variance_l DECIMAL(15,3),
  wet_variance_l DECIMAL(15,3),
  close_note VARCHAR(500),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_fuel_shift_no (branch_id, shift_no),
  KEY idx_fuel_shift_open (tenant_id, branch_id, cashier_id, status, opened_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fuel_shift_nozzle_meters (
  shift_id BIGINT NOT NULL,
  nozzle_id BIGINT NOT NULL,
  opening_meter_l DECIMAL(18,3) NOT NULL,
  closing_meter_l DECIMAL(18,3),
  PRIMARY KEY (shift_id, nozzle_id),
  CONSTRAINT fk_fuel_shift_meter_shift FOREIGN KEY (shift_id) REFERENCES fuel_shifts(id),
  CONSTRAINT fk_fuel_shift_meter_nozzle FOREIGN KEY (nozzle_id) REFERENCES fuel_nozzles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fuel_shift_tank_dips (
  shift_id BIGINT NOT NULL,
  tank_id BIGINT NOT NULL,
  opening_dip_l DECIMAL(15,3) NOT NULL,
  closing_dip_l DECIMAL(15,3),
  PRIMARY KEY (shift_id, tank_id),
  CONSTRAINT fk_fuel_shift_dip_shift FOREIGN KEY (shift_id) REFERENCES fuel_shifts(id),
  CONSTRAINT fk_fuel_shift_dip_tank FOREIGN KEY (tank_id) REFERENCES fuel_tanks(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fuel_sales (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  shift_id BIGINT NOT NULL,
  nozzle_id BIGINT NOT NULL,
  receipt_no VARCHAR(80) NOT NULL,
  cashier_id BIGINT NOT NULL,
  attendant_id BIGINT,
  litres DECIMAL(15,3) NOT NULL,
  unit_price DECIMAL(15,4) NOT NULL,
  amount DECIMAL(15,2) NOT NULL,
  currency VARCHAR(5) NOT NULL,
  payment_status VARCHAR(30) NOT NULL,
  fiscal_status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
  fiscal_reference VARCHAR(100),
  vehicle_reg VARCHAR(30),
  customer_ref VARCHAR(100),
  occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  idempotency_key VARCHAR(100) NOT NULL,
  voided BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_fuel_sale_idempotency (tenant_id, idempotency_key),
  UNIQUE KEY uk_fuel_sale_receipt (tenant_id, receipt_no),
  KEY idx_fuel_sale_report (tenant_id, branch_id, occurred_at, currency, voided),
  CONSTRAINT fk_fuel_sale_shift FOREIGN KEY (shift_id) REFERENCES fuel_shifts(id),
  CONSTRAINT fk_fuel_sale_nozzle FOREIGN KEY (nozzle_id) REFERENCES fuel_nozzles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fuel_sale_payments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  sale_id BIGINT NOT NULL,
  method VARCHAR(30) NOT NULL,
  currency VARCHAR(5) NOT NULL,
  amount DECIMAL(15,2) NOT NULL,
  reference VARCHAR(100),
  PRIMARY KEY (id),
  KEY idx_fuel_payment_sale (sale_id),
  CONSTRAINT fk_fuel_payment_sale FOREIGN KEY (sale_id) REFERENCES fuel_sales(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fuel_deliveries (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  tank_id BIGINT NOT NULL,
  supplier VARCHAR(160) NOT NULL,
  delivery_note VARCHAR(100) NOT NULL,
  invoice_l DECIMAL(15,3) NOT NULL,
  received_l DECIMAL(15,3) NOT NULL,
  before_dip_l DECIMAL(15,3) NOT NULL,
  after_dip_l DECIMAL(15,3) NOT NULL,
  variance_l DECIMAL(15,3) NOT NULL,
  cost_amount DECIMAL(15,2) NOT NULL,
  currency VARCHAR(5) NOT NULL,
  received_by BIGINT NOT NULL,
  received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_fuel_delivery_note (tenant_id, delivery_note),
  KEY idx_fuel_delivery_report (tenant_id, branch_id, received_at),
  CONSTRAINT fk_fuel_delivery_tank FOREIGN KEY (tank_id) REFERENCES fuel_tanks(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fuel_stock_movements (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  tank_id BIGINT NOT NULL,
  movement_type VARCHAR(30) NOT NULL,
  litres DECIMAL(15,3) NOT NULL,
  reference_type VARCHAR(30) NOT NULL,
  reference_id BIGINT NOT NULL,
  occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_fuel_stock_ledger (tenant_id, branch_id, tank_id, occurred_at),
  CONSTRAINT fk_fuel_movement_tank FOREIGN KEY (tank_id) REFERENCES fuel_tanks(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fuel_audit_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT,
  user_id BIGINT NOT NULL,
  action VARCHAR(80) NOT NULL,
  entity_type VARCHAR(50) NOT NULL,
  entity_id BIGINT,
  details VARCHAR(2000),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_fuel_audit (tenant_id, branch_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
