ALTER TABLE smile_pay_checkouts
    ADD COLUMN initiated_at DATETIME(6) NULL AFTER created_at,
    ADD COLUMN invoice_sent_at DATETIME(6) NULL AFTER paid_at;
