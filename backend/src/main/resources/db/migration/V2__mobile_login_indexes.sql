CREATE INDEX idx_users_username_mobile ON users (username);
CREATE INDEX idx_sales_tenant_branch_session_cashier ON sales (tenant_id, branch_id, cash_session_id, cashier_id);
CREATE INDEX idx_inventory_tenant_branch_product ON inventory (tenant_id, branch_id, product_id);
