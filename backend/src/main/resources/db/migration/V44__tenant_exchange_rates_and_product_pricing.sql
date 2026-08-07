CREATE TABLE IF NOT EXISTS tenant_exchange_rates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    base_currency VARCHAR(5) NOT NULL,
    quote_currency VARCHAR(5) NOT NULL,
    usd_to_zwg_rate DECIMAL(19,6) NOT NULL,
    price_scale TINYINT NOT NULL DEFAULT 2,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from DATETIME NOT NULL,
    effective_to DATETIME NULL,
    change_reason VARCHAR(500),
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    INDEX idx_exchange_rate_tenant_active (tenant_id, is_active, effective_from),
    INDEX idx_exchange_rate_tenant_history (tenant_id, effective_from),
    CONSTRAINT fk_exchange_rate_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT chk_exchange_rate_positive
        CHECK (usd_to_zwg_rate > 0 AND usd_to_zwg_rate <= 1000000),
    CONSTRAINT chk_exchange_rate_scale
        CHECK (price_scale BETWEEN 0 AND 4),
    CONSTRAINT chk_exchange_rate_currency_pair
        CHECK (base_currency <> quote_currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE products
    ADD COLUMN pricing_mode VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN exchange_rate_id BIGINT NULL,
    ADD INDEX idx_products_exchange_rate (exchange_rate_id),
    ADD CONSTRAINT fk_products_exchange_rate
        FOREIGN KEY (exchange_rate_id) REFERENCES tenant_exchange_rates(id) ON DELETE SET NULL;

ALTER TABLE sale_payments
    MODIFY exchange_rate DECIMAL(19,6) NOT NULL DEFAULT 1.000000,
    ADD COLUMN exchange_rate_config_id BIGINT NULL,
    ADD INDEX idx_sale_payments_exchange_rate (exchange_rate_config_id),
    ADD CONSTRAINT fk_sale_payments_exchange_rate
        FOREIGN KEY (exchange_rate_config_id) REFERENCES tenant_exchange_rates(id) ON DELETE SET NULL;
