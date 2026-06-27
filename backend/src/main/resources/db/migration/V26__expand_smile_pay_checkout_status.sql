ALTER TABLE smile_pay_checkouts
    MODIFY COLUMN status ENUM(
        'PENDING',
        'AWAITING_OTP',
        'PROCESSING',
        'PAID',
        'FAILED',
        'CANCELLED'
    ) NOT NULL DEFAULT 'PENDING';
