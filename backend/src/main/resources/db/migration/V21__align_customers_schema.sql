ALTER TABLE customers
    ADD COLUMN branch_id BIGINT NULL AFTER tenant_id,
    MODIFY COLUMN loyalty_card_number VARCHAR(50) NULL,
    MODIFY COLUMN total_spent_usd DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    MODIFY COLUMN total_spent_zwg DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN loyalty_points INT NULL DEFAULT 0 AFTER loyalty_card_number,
    ADD COLUMN total_transactions INT NULL DEFAULT 0 AFTER total_spent_zwg,
    ADD COLUMN store_credit_usd DECIMAL(15,2) NULL DEFAULT 0.00 AFTER total_transactions,
    ADD COLUMN store_credit_zwg DECIMAL(15,2) NULL DEFAULT 0.00 AFTER store_credit_usd,
    ADD COLUMN gender VARCHAR(10) NULL AFTER date_of_birth,
    ADD COLUMN notes TEXT NULL AFTER is_active;

UPDATE customers
SET branch_id = COALESCE(branch_id, registered_branch_id),
    loyalty_points = COALESCE(loyalty_points, loyalty_points_balance),
    total_transactions = COALESCE(total_transactions, 0),
    store_credit_usd = COALESCE(store_credit_usd, 0.00),
    store_credit_zwg = COALESCE(store_credit_zwg, 0.00);
