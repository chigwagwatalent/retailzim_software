ALTER TABLE gas_tanks
    ADD COLUMN tare_weight_kg DECIMAL(12,3) NOT NULL DEFAULT 0.000 AFTER product_name,
    ADD COLUMN full_gross_weight_kg DECIMAL(12,3) NULL AFTER capacity_kg,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER updated_at;

UPDATE gas_tanks
SET full_gross_weight_kg = tare_weight_kg + capacity_kg
WHERE full_gross_weight_kg IS NULL AND capacity_kg IS NOT NULL;

ALTER TABLE gas_shifts
    ADD COLUMN total_transactions INT NOT NULL DEFAULT 0 AFTER total_zwg,
    ADD COLUMN closing_variance_kg DECIMAL(12,3) NULL AFTER total_transactions,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER closing_variance_kg;

ALTER TABLE gas_sales
    ADD COLUMN amount_received DECIMAL(12,2) NULL AFTER total,
    ADD COLUMN change_due DECIMAL(12,2) NOT NULL DEFAULT 0.00 AFTER amount_received,
    ADD COLUMN change_held BOOLEAN NOT NULL DEFAULT FALSE AFTER change_due,
    ADD COLUMN offline_created_at DATETIME(6) NULL AFTER offline_receipt_number,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER created_at;

ALTER TABLE held_change
    ADD COLUMN gas_sale_id BIGINT NULL AFTER sale_id,
    ADD COLUMN gas_shift_id BIGINT NULL AFTER gas_sale_id;

CREATE TABLE gas_shift_tanks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    gas_shift_id BIGINT NOT NULL,
    tank_id BIGINT NOT NULL,
    starting_gross_kg DECIMAL(12,3) NOT NULL,
    starting_net_kg DECIMAL(12,3) NOT NULL,
    expected_closing_net_kg DECIMAL(12,3) NOT NULL,
    closing_gross_kg DECIMAL(12,3) NULL,
    closing_net_kg DECIMAL(12,3) NULL,
    variance_kg DECIMAL(12,3) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_USE',
    selected_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    closed_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gas_shift_tank (gas_shift_id, tank_id),
    KEY idx_gas_shift_tank_active (tenant_id, branch_id, status, gas_shift_id),
    KEY idx_gas_shift_tank_tank (tenant_id, branch_id, tank_id, status)
);

CREATE TABLE gas_sale_tank_allocations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    gas_sale_id BIGINT NOT NULL,
    gas_shift_id BIGINT NOT NULL,
    gas_shift_tank_id BIGINT NULL,
    tank_id BIGINT NOT NULL,
    quantity_kg DECIMAL(12,3) NOT NULL,
    stock_before_kg DECIMAL(12,3) NOT NULL,
    stock_after_kg DECIMAL(12,3) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_gas_sale_tank_allocation (gas_sale_id, tank_id),
    KEY idx_gas_sale_allocation_shift (tenant_id, branch_id, gas_shift_id),
    KEY idx_gas_sale_allocation_tank (tenant_id, branch_id, tank_id, created_at)
);

CREATE TABLE gas_sale_payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    gas_sale_id BIGINT NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    reference VARCHAR(120) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_gas_sale_payment_sale (gas_sale_id),
    KEY idx_gas_sale_payment_reporting (tenant_id, branch_id, created_at, payment_method)
);

CREATE INDEX idx_gas_sales_daily_reporting
    ON gas_sales (tenant_id, branch_id, created_at, currency, status);

CREATE INDEX idx_gas_held_change
    ON held_change (tenant_id, branch_id, gas_shift_id, status);
