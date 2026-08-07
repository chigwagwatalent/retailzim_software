CREATE TABLE IF NOT EXISTS product_wholesale_pricing (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    minimum_quantity DECIMAL(15,4) NULL,
    price_usd DECIMAL(15,4) NULL,
    price_zwg DECIMAL(15,4) NULL,
    pricing_mode VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    exchange_rate_id BIGINT NULL,
    updated_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_wholesale_tenant_product UNIQUE (tenant_id, product_id),
    INDEX idx_wholesale_tenant_enabled (tenant_id, is_enabled),
    INDEX idx_wholesale_exchange_rate (exchange_rate_id),
    CONSTRAINT fk_wholesale_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_wholesale_product
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_wholesale_exchange_rate
        FOREIGN KEY (exchange_rate_id) REFERENCES tenant_exchange_rates(id) ON DELETE SET NULL,
    CONSTRAINT chk_wholesale_minimum
        CHECK (minimum_quantity IS NULL OR minimum_quantity > 1),
    CONSTRAINT chk_wholesale_usd
        CHECK (price_usd IS NULL OR price_usd > 0),
    CONSTRAINT chk_wholesale_zwg
        CHECK (price_zwg IS NULL OR price_zwg > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE sale_items
    ADD COLUMN pricing_tier VARCHAR(20) NOT NULL DEFAULT 'RETAIL',
    ADD COLUMN retail_unit_price DECIMAL(15,4) NULL,
    ADD COLUMN wholesale_minimum_quantity DECIMAL(15,4) NULL,
    ADD COLUMN pricing_version BIGINT NULL,
    ADD COLUMN pricing_source VARCHAR(30) NOT NULL DEFAULT 'LEGACY_RETAIL',
    ADD COLUMN pricing_exchange_rate_id BIGINT NULL,
    ADD INDEX idx_sale_items_pricing_tier (pricing_tier),
    ADD INDEX idx_sale_items_pricing_exchange_rate (pricing_exchange_rate_id),
    ADD CONSTRAINT fk_sale_items_pricing_exchange_rate
        FOREIGN KEY (pricing_exchange_rate_id) REFERENCES tenant_exchange_rates(id) ON DELETE SET NULL;
