-- Java persists InventoryAdjustment.AdjustmentReason by name. Keep the database
-- column extensible so adding a valid application reason does not break writes.
ALTER TABLE inventory_adjustments
    MODIFY reason VARCHAR(30) NOT NULL;
