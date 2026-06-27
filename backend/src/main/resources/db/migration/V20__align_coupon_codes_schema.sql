ALTER TABLE coupon_codes
    MODIFY COLUMN promotion_id BIGINT NULL,
    ADD COLUMN discount_type ENUM('PERCENTAGE','FIXED_AMOUNT') NOT NULL DEFAULT 'PERCENTAGE' AFTER code,
    ADD COLUMN discount_value DECIMAL(10,4) NOT NULL DEFAULT 0.0000 AFTER discount_type,
    ADD COLUMN min_purchase_usd DECIMAL(10,2) NULL AFTER discount_value,
    ADD COLUMN max_discount_usd DECIMAL(10,2) NULL AFTER min_purchase_usd,
    ADD COLUMN usage_limit INT NULL AFTER max_discount_usd,
    ADD COLUMN usage_count INT NULL DEFAULT 0 AFTER usage_limit,
    ADD COLUMN per_customer_limit INT NULL DEFAULT 1 AFTER usage_count,
    ADD COLUMN starts_at DATETIME(6) NULL AFTER per_customer_limit,
    ADD COLUMN created_by BIGINT NULL AFTER is_active,
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) AFTER created_by;

UPDATE coupon_codes
SET usage_limit = COALESCE(usage_limit, max_uses),
    usage_count = COALESCE(usage_count, used_count),
    per_customer_limit = COALESCE(per_customer_limit, CASE WHEN is_single_use THEN 1 ELSE NULL END);
