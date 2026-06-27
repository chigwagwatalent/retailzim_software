ALTER TABLE cash_movements
    ADD COLUMN performed_by BIGINT NULL AFTER reason,
    MODIFY COLUMN type ENUM('CASH_IN','CASH_OUT','SAFE_DROP','FLOAT_ADD','FLOAT_REMOVE') NOT NULL,
    MODIFY COLUMN amount DECIMAL(15,2) NOT NULL;

UPDATE cash_movements
SET performed_by = COALESCE(performed_by, created_by);

ALTER TABLE cash_movements
    MODIFY COLUMN performed_by BIGINT NOT NULL;
