-- Composite indexes match equality scope followed by half-open reporting dates.
-- Separate tenant-wide index supports HQ comparisons without a per-branch query loop.
CREATE INDEX idx_sales_tenant_branch_status_date ON sales (tenant_id, branch_id, status, created_at);
CREATE INDEX idx_sales_tenant_status_date_branch ON sales (tenant_id, status, created_at, branch_id);
CREATE INDEX idx_payments_sale_currency_amount ON sale_payments (sale_id, currency, amount);
