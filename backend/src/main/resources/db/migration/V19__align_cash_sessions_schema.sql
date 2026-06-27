ALTER TABLE cash_sessions
    MODIFY COLUMN opening_float_usd DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    MODIFY COLUMN opening_float_zwg DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN closing_float_usd DECIMAL(15,2) NULL AFTER opening_float_zwg,
    ADD COLUMN closing_float_zwg DECIMAL(15,2) NULL AFTER closing_float_usd,
    ADD COLUMN actual_cash_usd DECIMAL(15,2) NULL AFTER closing_float_zwg,
    ADD COLUMN actual_cash_zwg DECIMAL(15,2) NULL AFTER actual_cash_usd,
    ADD COLUMN expected_cash_usd DECIMAL(15,2) NULL AFTER actual_cash_zwg,
    ADD COLUMN expected_cash_zwg DECIMAL(15,2) NULL AFTER expected_cash_usd,
    ADD COLUMN variance_usd_value DECIMAL(15,2) NULL AFTER expected_cash_zwg,
    ADD COLUMN variance_zwg DECIMAL(15,2) NULL AFTER variance_usd_value,
    ADD COLUMN total_sales_usd DECIMAL(15,2) NULL DEFAULT 0.00 AFTER variance_zwg,
    ADD COLUMN total_sales_zwg DECIMAL(15,2) NULL DEFAULT 0.00 AFTER total_sales_usd,
    ADD COLUMN total_transactions INT NULL DEFAULT 0 AFTER total_sales_zwg,
    ADD COLUMN closing_notes TEXT NULL AFTER total_transactions;

UPDATE cash_sessions
SET closing_float_usd = COALESCE(closing_float_usd, closing_actual_usd),
    closing_float_zwg = COALESCE(closing_float_zwg, closing_actual_zwg),
    actual_cash_usd = COALESCE(actual_cash_usd, closing_actual_usd),
    actual_cash_zwg = COALESCE(actual_cash_zwg, closing_actual_zwg),
    variance_usd_value = COALESCE(variance_usd_value, variance_usd),
    closing_notes = COALESCE(closing_notes, notes),
    total_sales_usd = COALESCE(total_sales_usd, 0.00),
    total_sales_zwg = COALESCE(total_sales_zwg, 0.00),
    total_transactions = COALESCE(total_transactions, 0);

ALTER TABLE cash_sessions
    DROP COLUMN variance_usd,
    CHANGE COLUMN variance_usd_value variance_usd DECIMAL(15,2) NULL;
