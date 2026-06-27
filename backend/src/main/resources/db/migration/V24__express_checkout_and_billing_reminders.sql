ALTER TABLE smile_pay_checkouts
    ADD COLUMN payment_method VARCHAR(20) NULL AFTER currency,
    ADD COLUMN customer_mobile VARCHAR(30) NULL AFTER payment_method,
    ADD COLUMN expires_at DATETIME(6) NULL AFTER paid_at,
    ADD COLUMN last_checked_at DATETIME(6) NULL AFTER expires_at;

CREATE INDEX idx_smile_pay_tenant_status
    ON smile_pay_checkouts (tenant_id, status);

CREATE TABLE IF NOT EXISTS billing_reminder_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    reminder_key VARCHAR(40) NOT NULL,
    recipient VARCHAR(150) NOT NULL,
    checkout_reference VARCHAR(80) NULL,
    sent_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_billing_reminder_delivery (subscription_id, reminder_key),
    KEY idx_billing_reminder_tenant (tenant_id),
    CONSTRAINT fk_billing_reminder_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_billing_reminder_subscription
        FOREIGN KEY (subscription_id) REFERENCES tenant_subscriptions(id)
);
