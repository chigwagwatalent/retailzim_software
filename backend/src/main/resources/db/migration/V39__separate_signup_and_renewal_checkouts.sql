ALTER TABLE smile_pay_checkouts
    ADD COLUMN checkout_purpose VARCHAR(30) NOT NULL DEFAULT 'SIGNUP_ACTIVATION' AFTER plan_id,
    ADD COLUMN billing_months INT NOT NULL DEFAULT 1 AFTER checkout_purpose,
    ADD COLUMN unit_price DECIMAL(15, 2) NULL AFTER billing_months,
    ADD COLUMN previous_period_end DATETIME(6) NULL AFTER unit_price,
    ADD COLUMN new_period_end DATETIME(6) NULL AFTER previous_period_end,
    ADD COLUMN created_by_user_id BIGINT NULL AFTER new_period_end,
    ADD COLUMN access_token VARCHAR(96) NULL AFTER order_reference,
    ADD COLUMN next_check_at DATETIME(6) NULL AFTER last_checked_at,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER updated_at;

UPDATE smile_pay_checkouts
SET unit_price = amount
WHERE unit_price IS NULL;

UPDATE smile_pay_checkouts
SET access_token = CONCAT(REPLACE(UUID(), '-', ''), REPLACE(UUID(), '-', ''))
WHERE access_token IS NULL;

ALTER TABLE smile_pay_checkouts
    MODIFY COLUMN unit_price DECIMAL(15, 2) NOT NULL,
    MODIFY COLUMN access_token VARCHAR(96) NOT NULL;

CREATE UNIQUE INDEX uk_smile_pay_access_token
    ON smile_pay_checkouts (access_token);

CREATE INDEX idx_smile_pay_reusable_checkout
    ON smile_pay_checkouts (
        tenant_id,
        plan_id,
        checkout_purpose,
        billing_months,
        status,
        created_at
    );

CREATE INDEX idx_smile_pay_reconciliation
    ON smile_pay_checkouts (status, next_check_at, initiated_at);

CREATE TABLE subscription_payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    checkout_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    checkout_purpose VARCHAR(30) NOT NULL,
    billing_months INT NOT NULL,
    unit_price DECIMAL(15, 2) NOT NULL,
    total_amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(5) NOT NULL,
    payment_method VARCHAR(20) NULL,
    order_reference VARCHAR(80) NOT NULL,
    provider_reference VARCHAR(150) NULL,
    previous_period_end DATETIME(6) NULL,
    new_period_end DATETIME(6) NOT NULL,
    confirmed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_subscription_payment_checkout (checkout_id),
    UNIQUE KEY uk_subscription_payment_order (order_reference),
    KEY idx_subscription_payment_tenant_date (tenant_id, confirmed_at),
    KEY idx_subscription_payment_provider (provider_reference),
    CONSTRAINT fk_subscription_payment_checkout
        FOREIGN KEY (checkout_id) REFERENCES smile_pay_checkouts(id),
    CONSTRAINT fk_subscription_payment_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_subscription_payment_plan
        FOREIGN KEY (plan_id) REFERENCES saas_plans(id)
);

CREATE TABLE payment_notification_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    checkout_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    claimed_until DATETIME(6) NULL,
    last_error VARCHAR(1000) NULL,
    sent_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_notification_checkout (checkout_id),
    KEY idx_payment_notification_due (status, next_attempt_at, claimed_until),
    CONSTRAINT fk_payment_notification_checkout
        FOREIGN KEY (checkout_id) REFERENCES smile_pay_checkouts(id),
    CONSTRAINT fk_payment_notification_subscription
        FOREIGN KEY (subscription_id) REFERENCES tenant_subscriptions(id)
);
