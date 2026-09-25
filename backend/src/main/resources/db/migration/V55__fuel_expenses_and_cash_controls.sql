CREATE TABLE fuel_expenses (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 branch_id BIGINT NOT NULL,
 category VARCHAR(80) NOT NULL,
 description VARCHAR(500) NOT NULL,
 amount DECIMAL(15,2) NOT NULL,
 currency VARCHAR(5) NOT NULL,
 reference VARCHAR(100) NOT NULL,
 incurred_on DATE NOT NULL,
 created_by BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_fuel_expense_reference(tenant_id,reference),
 KEY idx_fuel_expense_report(tenant_id,branch_id,incurred_on)
);
