ALTER TABLE attendance_records
    ADD COLUMN attendance_date DATE NULL AFTER branch_id,
    ADD COLUMN clock_in_at DATETIME(6) NULL AFTER attendance_date,
    ADD COLUMN clock_out_at DATETIME(6) NULL AFTER clock_in_at,
    ADD COLUMN status VARCHAR(20) NULL DEFAULT 'PRESENT' AFTER hours_worked;

UPDATE attendance_records
SET attendance_date = COALESCE(attendance_date, `date`),
    clock_in_at = COALESCE(clock_in_at, clock_in_time),
    clock_out_at = COALESCE(clock_out_at, clock_out_time),
    status = COALESCE(status, 'PRESENT');

ALTER TABLE attendance_records
    MODIFY COLUMN attendance_date DATE NOT NULL,
    MODIFY COLUMN status VARCHAR(20) NULL DEFAULT 'PRESENT';

CREATE INDEX idx_attendance_records_attendance_date
    ON attendance_records (attendance_date);
