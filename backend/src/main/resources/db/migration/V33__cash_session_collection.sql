ALTER TABLE cash_sessions
    ADD COLUMN cash_collected BOOLEAN NOT NULL DEFAULT FALSE AFTER closing_notes,
    ADD COLUMN collected_cash_usd DECIMAL(15,2) NULL AFTER cash_collected,
    ADD COLUMN collected_cash_zwg DECIMAL(15,2) NULL AFTER collected_cash_usd,
    ADD COLUMN collection_variance_usd DECIMAL(15,2) NULL AFTER collected_cash_zwg,
    ADD COLUMN collection_variance_zwg DECIMAL(15,2) NULL AFTER collection_variance_usd,
    ADD COLUMN collection_notes TEXT NULL AFTER collection_variance_zwg,
    ADD COLUMN collected_by_user_id BIGINT NULL AFTER collection_notes,
    ADD COLUMN collected_at DATETIME NULL AFTER collected_by_user_id;

CREATE INDEX idx_cash_sessions_collection
    ON cash_sessions (tenant_id, branch_id, status, cash_collected);
