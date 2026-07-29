UPDATE gas_tanks
SET capacity_kg = 0.000
WHERE capacity_kg IS NULL OR capacity_kg < 0.000;

UPDATE gas_tanks
SET full_gross_weight_kg = tare_weight_kg + capacity_kg
WHERE full_gross_weight_kg IS NULL
   OR full_gross_weight_kg < tare_weight_kg;

UPDATE gas_tanks
SET capacity_kg = full_gross_weight_kg - tare_weight_kg
WHERE capacity_kg <> full_gross_weight_kg - tare_weight_kg;

UPDATE gas_tanks
SET current_kg = LEAST(GREATEST(current_kg, 0.000), capacity_kg);

UPDATE gas_tanks
SET reorder_level_kg = LEAST(
        GREATEST(COALESCE(reorder_level_kg, 0.000), 0.000),
        capacity_kg
    );

ALTER TABLE gas_tanks
    MODIFY capacity_kg DECIMAL(12,3) NOT NULL,
    MODIFY full_gross_weight_kg DECIMAL(12,3) NOT NULL,
    MODIFY reorder_level_kg DECIMAL(12,3) NOT NULL DEFAULT 0.000,
    ADD CONSTRAINT chk_gas_tank_weight_order
        CHECK (tare_weight_kg >= 0.000 AND full_gross_weight_kg >= tare_weight_kg),
    ADD CONSTRAINT chk_gas_tank_derived_capacity
        CHECK (capacity_kg = full_gross_weight_kg - tare_weight_kg),
    ADD CONSTRAINT chk_gas_tank_current_net
        CHECK (current_kg >= 0.000 AND current_kg <= capacity_kg),
    ADD CONSTRAINT chk_gas_tank_reorder_level
        CHECK (reorder_level_kg >= 0.000 AND reorder_level_kg <= capacity_kg);
