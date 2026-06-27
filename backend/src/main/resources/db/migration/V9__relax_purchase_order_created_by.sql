-- Older running builds and system-generated reorder jobs can create purchase orders
-- without a user id. The entity allows null, so keep the database aligned.

ALTER TABLE purchase_orders
    MODIFY created_by BIGINT NULL;
