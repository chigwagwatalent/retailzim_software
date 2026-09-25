CREATE TABLE retail_expenses (
 id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, branch_id BIGINT NOT NULL,
 expense_number VARCHAR(50) NOT NULL, description VARCHAR(180) NOT NULL, vendor VARCHAR(150), category VARCHAR(40) NOT NULL,
 amount DECIMAL(15,2) NOT NULL, currency VARCHAR(5) NOT NULL, payment_method VARCHAR(30) NOT NULL,
 payment_reference VARCHAR(120), incurred_on DATE NOT NULL, notes TEXT, status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
 created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL, voided_by BIGINT, void_reason VARCHAR(300), voided_at DATETIME(6),
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), version BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(id), UNIQUE KEY uk_retail_expense_tenant_number(tenant_id,expense_number),
 KEY idx_retail_expense_report(tenant_id,branch_id,status,incurred_on,currency),
 KEY idx_retail_expense_tenant_date(tenant_id,incurred_on), KEY idx_retail_expense_category(tenant_id,category,incurred_on),
 CONSTRAINT fk_retail_expense_tenant FOREIGN KEY(tenant_id) REFERENCES tenants(id),
 CONSTRAINT fk_retail_expense_branch FOREIGN KEY(branch_id) REFERENCES branches(id),
 CONSTRAINT chk_retail_expense_amount CHECK(amount > 0)
);
