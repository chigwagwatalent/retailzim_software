CREATE INDEX idx_tenant_registration_date_id ON tenants (created_at, id);
CREATE INDEX idx_tenant_subscription_end ON tenants (subscription_end);
