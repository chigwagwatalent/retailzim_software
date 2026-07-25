CREATE INDEX idx_subscription_payment_accounting
    ON subscription_payments (confirmed_at, currency);

CREATE INDEX idx_tenant_admin_directory
    ON tenants (status, plan_id, created_at);
