-- Current subscriptions use starts_at/ends_at and derive the billing cycle
-- from the selected plan. Keep legacy data readable without requiring those
-- retired columns on new subscription records.
ALTER TABLE tenant_subscriptions
    MODIFY COLUMN billing_cycle ENUM('MONTHLY', 'QUARTERLY', 'ANNUALLY') NULL,
    MODIFY COLUMN start_date DATE NULL,
    MODIFY COLUMN end_date DATE NULL;
